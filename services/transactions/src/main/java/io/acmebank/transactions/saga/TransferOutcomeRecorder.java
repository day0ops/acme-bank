package io.acmebank.transactions.saga;

import io.acmebank.transactions.domain.Transaction;
import io.acmebank.transactions.persistence.OutboxRepository;
import io.acmebank.transactions.persistence.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    public TransferOutcomeRecorder(TransactionRepository transactionRepository, OutboxRepository outboxRepository) {
        this.transactionRepository = transactionRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public Transaction markCompleted(Transaction transaction) {
        Transaction completed = transactionRepository.save(transaction.complete());
        outboxRepository.append("Transaction", completed.id(), "TransactionCompleted", toProtoBytes(completed));
        return completed;
    }

    @Transactional
    public Transaction markFailed(Transaction transaction, String reason) {
        Transaction failed = transactionRepository.save(transaction.fail(reason));
        outboxRepository.append("Transaction", failed.id(), "TransactionFailed", toProtoBytes(failed));
        return failed;
    }

    private byte[] toProtoBytes(Transaction transaction) {
        return io.acmebank.proto.transactions.v1.Transaction.newBuilder()
                .setId(transaction.id())
                .setFromAccountId(transaction.fromAccountId())
                .setToAccountId(transaction.toAccountId())
                .setAmount(io.acmebank.proto.common.v1.Money.newBuilder()
                        .setCurrencyCode(transaction.currencyCode())
                        .setMinorUnits(transaction.amountMinorUnits())
                        .build())
                .setStatus(toProtoStatus(transaction.status()))
                .build()
                .toByteArray();
    }

    private static io.acmebank.proto.transactions.v1.TransactionStatus toProtoStatus(io.acmebank.transactions.domain.TransactionStatus status) {
        return switch (status) {
            case PENDING -> io.acmebank.proto.transactions.v1.TransactionStatus.TRANSACTION_STATUS_PENDING;
            case COMPLETED -> io.acmebank.proto.transactions.v1.TransactionStatus.TRANSACTION_STATUS_COMPLETED;
            case FAILED -> io.acmebank.proto.transactions.v1.TransactionStatus.TRANSACTION_STATUS_FAILED;
        };
    }
}
