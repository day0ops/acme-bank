package io.acmebank.transactions.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.acmebank.transactions.domain.Transaction;
import io.acmebank.transactions.persistence.OutboxRepository;
import io.acmebank.transactions.persistence.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Persists a transfer's terminal outcome and its outbox event atomically.
 * This is a separate bean (not methods on TransferSaga) because Spring's
 * proxy-based @Transactional cannot intercept self-invoked calls within
 * the same bean - crossing a real bean boundary is required for the
 * transaction annotation to actually apply.
 */
@Component
public class TransferOutcomeRecorder {

    private final TransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransferOutcomeRecorder(TransactionRepository transactionRepository, OutboxRepository outboxRepository) {
        this.transactionRepository = transactionRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public Transaction markCompleted(Transaction transaction) {
        Transaction completed = transactionRepository.save(transaction.complete());
        outboxRepository.append("Transaction", completed.id(), "TransactionCompleted", toJson(completed));
        return completed;
    }

    @Transactional
    public Transaction markFailed(Transaction transaction, String reason) {
        Transaction failed = transactionRepository.save(transaction.fail(reason));
        outboxRepository.append("Transaction", failed.id(), "TransactionFailed", toJson(failed));
        return failed;
    }

    private String toJson(Transaction transaction) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "id", transaction.id(),
                    "fromAccountId", transaction.fromAccountId(),
                    "toAccountId", transaction.toAccountId(),
                    "status", transaction.status().toString()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize outbox event payload", e);
        }
    }
}
