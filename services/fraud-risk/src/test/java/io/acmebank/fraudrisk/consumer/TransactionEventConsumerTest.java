package io.acmebank.fraudrisk.consumer;

import io.acmebank.fraudrisk.persistence.FlaggedTransactionRepository;
import io.acmebank.proto.common.v1.Money;
import io.acmebank.proto.fraud.v1.TransactionFlaggedEvent;
import io.acmebank.proto.transactions.v1.Transaction;
import io.acmebank.proto.transactions.v1.TransactionStatus;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.redpanda.RedpandaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class TransactionEventConsumerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fraud_risk").withUsername("fraud_risk").withPassword("fraud_risk");

    // Matches the image deployed by deploy/helm/redpanda, consistent with Transactions'
    // OutboxRelayTest - keeps this test closer to what actually runs in the cluster than a
    // generic wire-compatible Kafka broker would.
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
    private KafkaTemplate<String, byte[]> kafkaTemplate;
    @Autowired
    private FlaggedTransactionRepository flaggedTransactionRepository;

    @Test
    void consume_transactionAboveThreshold_persistsFlag() {
        Transaction tx = Transaction.newBuilder()
                .setId("22222222-2222-2222-2222-222222222222")
                .setFromAccountId("33333333-3333-3333-3333-333333333333")
                .setToAccountId("44444444-4444-4444-4444-444444444444")
                .setAmount(Money.newBuilder().setCurrencyCode("USD").setMinorUnits(5_000_000L).build())
                .setStatus(TransactionStatus.TRANSACTION_STATUS_COMPLETED)
                .build();

        var message = MessageBuilder.withPayload(tx.toByteArray())
                .setHeader(KafkaHeaders.TOPIC, "transactions.events")
                .setHeader("event-type", "TransactionCompleted")
                .build();
        kafkaTemplate.send(message);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertTrue(flaggedTransactionRepository.findAll().stream()
                        .anyMatch(f -> f.transactionId().equals("22222222-2222-2222-2222-222222222222"))));
    }

    @Test
    void consume_transactionFailedAboveThreshold_doesNotPersistFlag() {
        String transactionId = "55555555-5555-5555-5555-555555555555";
        Transaction tx = Transaction.newBuilder()
                .setId(transactionId)
                .setFromAccountId("33333333-3333-3333-3333-333333333333")
                .setToAccountId("44444444-4444-4444-4444-444444444444")
                .setAmount(Money.newBuilder().setCurrencyCode("USD").setMinorUnits(5_000_000L).build())
                .setStatus(TransactionStatus.TRANSACTION_STATUS_FAILED)
                .build();

        var message = MessageBuilder.withPayload(tx.toByteArray())
                .setHeader(KafkaHeaders.TOPIC, "transactions.events")
                .setHeader("event-type", "TransactionFailed")
                .build();
        kafkaTemplate.send(message);

        // Proving absence: a fixed wait window, not untilAsserted, since there is no
        // "it happened" signal to poll for here.
        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertFalse(flaggedTransactionRepository.findAll().stream()
                        .anyMatch(f -> f.transactionId().equals(transactionId))));
    }

    @Test
    void consume_transactionCompletedAboveThreshold_publishesFlaggedEventWithCorrectFields() throws Exception {
        String transactionId = "66666666-6666-6666-6666-666666666666";
        Transaction tx = Transaction.newBuilder()
                .setId(transactionId)
                .setFromAccountId("33333333-3333-3333-3333-333333333333")
                .setToAccountId("44444444-4444-4444-4444-444444444444")
                .setAmount(Money.newBuilder().setCurrencyCode("USD").setMinorUnits(5_000_000L).build())
                .setStatus(TransactionStatus.TRANSACTION_STATUS_COMPLETED)
                .build();

        var message = MessageBuilder.withPayload(tx.toByteArray())
                .setHeader(KafkaHeaders.TOPIC, "transactions.events")
                .setHeader("event-type", "TransactionCompleted")
                .build();
        kafkaTemplate.send(message);

        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", redpanda.getBootstrapServers());
        consumerProps.put("group.id", "test-consumer-" + transactionId);
        consumerProps.put("auto.offset.reset", "earliest");
        consumerProps.put("key.deserializer", StringDeserializer.class.getName());
        consumerProps.put("value.deserializer", ByteArrayDeserializer.class.getName());

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("fraud.events"));

            TransactionFlaggedEvent flaggedEvent = null;
            long deadline = System.currentTimeMillis() + Duration.ofSeconds(15).toMillis();
            while (flaggedEvent == null && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, byte[]> record : records) {
                    TransactionFlaggedEvent candidate = TransactionFlaggedEvent.parseFrom(record.value());
                    if (transactionId.equals(candidate.getTransactionId())) {
                        flaggedEvent = candidate;
                        break;
                    }
                }
            }

            assertNotNull(flaggedEvent, "expected a TransactionFlaggedEvent for transaction " + transactionId);
            assertEquals(transactionId, flaggedEvent.getTransactionId());
            assertTrue(flaggedEvent.getReason().contains("threshold"));
            assertEquals("USD", flaggedEvent.getAmount().getCurrencyCode());
            assertEquals(5_000_000L, flaggedEvent.getAmount().getMinorUnits());
        }
    }
}
