package io.acmebank.fraudrisk.consumer;

import com.google.protobuf.InvalidProtocolBufferException;
import io.acmebank.fraudrisk.domain.FlaggedTransaction;
import io.acmebank.fraudrisk.persistence.FlaggedTransactionRepository;
import io.acmebank.fraudrisk.rules.FraudRule;
import io.acmebank.proto.common.v1.Money;
import io.acmebank.proto.fraud.v1.TransactionFlaggedEvent;
import io.acmebank.proto.transactions.v1.Transaction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code transactions.events} and applies the fraud/risk rule set to every
 * message on the topic. Transactions flagged by {@link FraudRule} are persisted and
 * republished on {@code fraud.events}.
 */
@Component
public class TransactionEventConsumer {

    private static final String FRAUD_EVENTS_TOPIC = "fraud.events";

    private final FlaggedTransactionRepository repository;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public TransactionEventConsumer(FlaggedTransactionRepository repository, KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "transactions.events", groupId = "fraud-risk")
    public void onMessage(ConsumerRecord<String, byte[]> record) throws InvalidProtocolBufferException {
        Transaction tx = Transaction.parseFrom(record.value());
        FraudRule.evaluate(tx.getAmount().getMinorUnits(), tx.getAmount().getCurrencyCode())
                .ifPresent(reason -> {
                    FlaggedTransaction flagged = FlaggedTransaction.flag(
                            tx.getId(), reason, tx.getAmount().getMinorUnits(), tx.getAmount().getCurrencyCode());
                    repository.save(flagged);
                    publishFlagged(tx.getId(), reason, tx.getAmount());
                });
    }

    private void publishFlagged(String transactionId, String reason, Money amount) {
        TransactionFlaggedEvent event = TransactionFlaggedEvent.newBuilder()
                .setTransactionId(transactionId)
                .setReason(reason)
                .setAmount(amount)
                .build();
        kafkaTemplate.send(FRAUD_EVENTS_TOPIC, transactionId, event.toByteArray());
    }
}
