package io.acmebank.transactions.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

interface TransactionJpaRepository extends JpaRepository<TransactionEntity, UUID> {
    List<TransactionEntity> findByFromAccountIdOrToAccountId(UUID fromAccountId, UUID toAccountId);
}
