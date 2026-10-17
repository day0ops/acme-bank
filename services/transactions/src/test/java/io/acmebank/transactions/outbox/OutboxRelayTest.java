package io.acmebank.transactions.outbox;

import io.acmebank.transactions.persistence.OutboxRepository;
import io.opentelemetry.api.OpenTelemetry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class OutboxRelayTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transactions").withUsername("transactions").withPassword("transactions");

    // Matches the image deployed by deploy/helm/redpanda (Task 1), not just a wire-compatible
    // generic Kafka broker - keeps this test closer to what actually runs in the cluster.
    @Container
    static RedpandaContainer redpanda = new RedpandaContainer("redpandadata/redpanda:v24.2.7");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", redpanda::getBootstrapServers);
    }

    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private OutboxRelay relay;
    @Autowired
    private OpenTelemetry openTelemetry;

    @Test
    void relay_publishesUnpublishedEventAndMarksPublished() {
        outboxRepository.append("Transaction", "11111111-1111-1111-1111-111111111111", "TransactionCompleted", "test-payload".getBytes());

        relay.pollAndPublish();

        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", redpanda.getBootstrapServers());
        consumerProps.put("group.id", "test-consumer");
        consumerProps.put("auto.offset.reset", "earliest");
        consumerProps.put("key.deserializer", StringDeserializer.class.getName());
        consumerProps.put("value.deserializer", ByteArrayDeserializer.class.getName());

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(java.util.List.of("transactions.events"));
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(10));
            assertEquals(1, records.count());
            ConsumerRecord<String, byte[]> record = records.iterator().next();
            assertEquals("test-payload", new String(record.value()));
            assertEquals("TransactionCompleted", new String(record.headers().lastHeader("event-type").value()));
            // Must be a well-formed W3C traceparent, not just present: an empty-but-present
            // header is how the original bug (always-invalid ambient span) went undetected.
            String traceParentValue = new String(record.headers().lastHeader("traceparent").value());
            assertTrue(traceParentValue.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"),
                    "traceparent should be a well-formed W3C traceparent, got: " + traceParentValue);
        }

        var unpublished = outboxRepository.findUnpublished();
        assertTrue(unpublished.isEmpty());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void relay_whenKafkaSendFails_leavesEventUnpublishedForRetry() {
        String aggregateId = "22222222-2222-2222-2222-222222222222";
        outboxRepository.append("Transaction", aggregateId, "TransactionCompleted", "test-payload".getBytes());

        KafkaTemplate<String, byte[]> failingKafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<Object> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new KafkaException("broker unavailable"));
        when(failingKafkaTemplate.send(any(Message.class))).thenReturn((CompletableFuture) failedSend);

        OutboxRelay failingRelay = new OutboxRelay(outboxRepository, failingKafkaTemplate, openTelemetry);

        assertDoesNotThrow(failingRelay::pollAndPublish);

        var stillUnpublished = outboxRepository.findUnpublished().stream()
                .filter(event -> aggregateId.equals(event.aggregateId()))
                .findFirst();
        assertTrue(stillUnpublished.isPresent(), "event should remain unpublished so the next poll retries it");

        // This row can never succeed (its broker was a mock), so mark it published to keep
        // the shared Testcontainers database isolated from other tests in this class that
        // assert on the full set of unpublished rows.
        outboxRepository.markPublished(stillUnpublished.get().id());
    }
}
