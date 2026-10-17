package io.acmebank.fraudrisk.consumer;

import io.acmebank.fraudrisk.persistence.FlaggedTransactionRepository;
import io.acmebank.proto.common.v1.Money;
import io.acmebank.proto.transactions.v1.Transaction;
import io.acmebank.proto.transactions.v1.TransactionStatus;
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
}
