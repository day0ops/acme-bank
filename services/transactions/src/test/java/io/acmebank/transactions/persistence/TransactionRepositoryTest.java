package io.acmebank.transactions.persistence;

import io.acmebank.transactions.domain.Transaction;
import io.acmebank.transactions.domain.TransactionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class TransactionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transactions").withUsername("transactions").withPassword("transactions");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TransactionRepository repository;

    @Autowired
    private TransactionJpaRepository jpaRepository;

    @Test
    void save_thenFindById_roundTrips() {
        Transaction tx = Transaction.initiate("11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222", "USD", 5000L);

        Transaction saved = repository.save(tx);

        assertNotNull(saved.id());
        Optional<Transaction> found = repository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals(5000L, found.get().amountMinorUnits());
    }

    @Test
    void save_withExistingId_updatesInPlace() {
        Transaction tx = Transaction.initiate("11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222", "USD", 5000L);
        Transaction saved = repository.save(tx);

        Transaction completed = saved.complete();
        Transaction updated = repository.save(completed);

        assertEquals(saved.id(), updated.id());
        assertEquals(TransactionStatus.COMPLETED, updated.status());
    }

    @Test
    void save_withExistingId_updatesInPlace_notDuplicate() {
        // Row count is asserted as a delta since the Testcontainers database is shared
        // across test methods in this class with no reset between tests.
        long countBefore = jpaRepository.count();
        Transaction tx = Transaction.initiate("11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222", "USD", 7500L);
        Transaction saved = repository.save(tx);

        Transaction failed = saved.fail("insufficient funds");
        Transaction updated = repository.save(failed);

        assertEquals(saved.id(), updated.id());
        assertEquals(countBefore + 1, jpaRepository.count());
        Optional<Transaction> found = repository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals(TransactionStatus.FAILED, found.get().status());
        assertEquals("insufficient funds", found.get().failureReason());
    }

    @Test
    void findById_withUnknownId_returnsEmpty() {
        Optional<Transaction> found = repository.findById("00000000-0000-0000-0000-000000000000");
        assertTrue(found.isEmpty());
    }
}
