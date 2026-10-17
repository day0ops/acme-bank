package io.acmebank.fraudrisk.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface FlaggedTransactionJpaRepository extends JpaRepository<FlaggedTransactionEntity, UUID> {}
