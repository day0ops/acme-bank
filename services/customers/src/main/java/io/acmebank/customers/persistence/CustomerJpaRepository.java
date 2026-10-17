package io.acmebank.customers.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {}
