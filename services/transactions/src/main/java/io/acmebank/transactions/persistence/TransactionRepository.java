package io.acmebank.transactions.persistence;

import io.acmebank.transactions.domain.Transaction;
import io.acmebank.transactions.domain.TransactionStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class TransactionRepository {

    private final TransactionJpaRepository jpaRepository;

    public TransactionRepository(TransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Inserts a new transaction when {@code transaction.id()} is null; otherwise loads and mutates
     * the managed entity so the update persists in place instead of inserting a duplicate row.
     *
     * @param transaction the transaction to insert or update
     * @return the persisted transaction, including its id
     * @throws IllegalArgumentException if {@code transaction.id()} is set but no such transaction exists
     */
    public Transaction save(Transaction transaction) {
        if (transaction.id() == null) {
            TransactionEntity entity = new TransactionEntity(
                    UUID.fromString(transaction.fromAccountId()),
                    UUID.fromString(transaction.toAccountId()),
                    transaction.currencyCode(),
                    transaction.amountMinorUnits(),
                    statusToShort(transaction.status()),
                    transaction.failureReason());
            return toDomain(jpaRepository.save(entity));
        }
        TransactionEntity entity = jpaRepository.findById(UUID.fromString(transaction.id()))
                .orElseThrow(() -> new IllegalArgumentException("transaction " + transaction.id() + " not found"));
        entity.setStatus(statusToShort(transaction.status()));
        entity.setFailureReason(transaction.failureReason());
        return toDomain(jpaRepository.save(entity));
    }

    public Optional<Transaction> findById(String id) {
        try {
            return jpaRepository.findById(UUID.fromString(id)).map(TransactionRepository::toDomain);
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }

    public List<Transaction> findByAccountId(String accountId) {
        UUID id = UUID.fromString(accountId);
        return jpaRepository.findByFromAccountIdOrToAccountId(id, id).stream()
                .map(TransactionRepository::toDomain)
                .collect(Collectors.toList());
    }

    private static short statusToShort(TransactionStatus status) {
        return switch (status) {
            case PENDING -> 1;
            case COMPLETED -> 2;
            case FAILED -> 3;
        };
    }

    private static TransactionStatus shortToStatus(short value) {
        return switch (value) {
            case 2 -> TransactionStatus.COMPLETED;
            case 3 -> TransactionStatus.FAILED;
            default -> TransactionStatus.PENDING;
        };
    }

    private static Transaction toDomain(TransactionEntity entity) {
        return new Transaction(
                entity.getId().toString(),
                entity.getFromAccountId().toString(),
                entity.getToAccountId().toString(),
                entity.getCurrencyCode(),
                entity.getAmountMinor(),
                shortToStatus(entity.getStatus()),
                entity.getFailureReason());
    }
}
