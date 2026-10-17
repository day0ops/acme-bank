package io.acmebank.transactions.outbox;

import io.acmebank.transactions.persistence.OutboxRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

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
            // Header must be present for cross-service trace continuity even when there's no active
            // span in the polling thread (the relay still sets it, just to an empty value in that case).
            assertNotNull(record.headers().lastHeader("traceparent"));
        }

        var unpublished = outboxRepository.findUnpublished();
        assertTrue(unpublished.isEmpty());
    }
}
