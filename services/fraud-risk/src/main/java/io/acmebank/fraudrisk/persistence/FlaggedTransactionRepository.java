package io.acmebank.fraudrisk.persistence;

import io.acmebank.fraudrisk.domain.FlaggedTransaction;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class FlaggedTransactionRepository {

    private final FlaggedTransactionJpaRepository jpaRepository;

    public FlaggedTransactionRepository(FlaggedTransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public FlaggedTransaction save(FlaggedTransaction flagged) {
        FlaggedTransactionEntity entity = new FlaggedTransactionEntity(
                UUID.fromString(flagged.transactionId()), flagged.reason(), flagged.currencyCode(), flagged.amountMinorUnits());
        FlaggedTransactionEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    public List<FlaggedTransaction> findAll() {
        return jpaRepository.findAll().stream().map(FlaggedTransactionRepository::toDomain).collect(Collectors.toList());
    }

    private static FlaggedTransaction toDomain(FlaggedTransactionEntity entity) {
        return new FlaggedTransaction(
                entity.getId().toString(), entity.getTransactionId().toString(),
                entity.getReason(), entity.getAmountMinor(), entity.getCurrencyCode());
    }
}
