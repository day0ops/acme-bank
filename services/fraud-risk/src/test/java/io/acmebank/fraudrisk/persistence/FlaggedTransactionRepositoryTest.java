package io.acmebank.fraudrisk.persistence;

import io.acmebank.fraudrisk.domain.FlaggedTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class FlaggedTransactionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fraud_risk").withUsername("fraud_risk").withPassword("fraud_risk");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private FlaggedTransactionRepository repository;

    @Test
    void save_thenFindAll_includesSavedRecord() {
        FlaggedTransaction flagged = FlaggedTransaction.flag(
                "11111111-1111-1111-1111-111111111111", "amount exceeds threshold", 1_500_000L, "USD");

        FlaggedTransaction saved = repository.save(flagged);

        assertNotNull(saved.id());
        assertTrue(repository.findAll().stream().anyMatch(f -> f.id().equals(saved.id())));
    }
}
