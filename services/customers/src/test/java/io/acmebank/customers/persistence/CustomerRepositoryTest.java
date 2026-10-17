package io.acmebank.customers.persistence;

import io.acmebank.customers.domain.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class CustomerRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("customers")
            .withUsername("customers")
            .withPassword("customers");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CustomerRepository repository;

    @Autowired
    private CustomerJpaRepository jpaRepository;

    @Test
    void save_thenFindById_roundTrips() {
        Customer created = Customer.create("Grace", "Hopper", "grace@example.com", LocalDate.of(1906, 12, 9));

        Customer saved = repository.save(created);

        assertNotNull(saved.id());
        Optional<Customer> found = repository.findById(saved.id());
        assertTrue(found.isPresent());
        assertEquals("grace@example.com", found.get().email());
    }

    @Test
    void findById_withUnknownId_returnsEmpty() {
        Optional<Customer> found = repository.findById("00000000-0000-0000-0000-000000000000");
        assertTrue(found.isEmpty());
    }

    @Test
    void update_existingCustomer_updatesInPlace_notDuplicate() {
        // Row count is asserted as a delta since the Testcontainers database is shared
        // across test methods in this class with no reset between tests.
        long countBefore = jpaRepository.count();
        Customer created = Customer.create("Original", "Lovelace", "ada@example.com", LocalDate.of(1815, 12, 10));
        Customer saved = repository.save(created);

        Customer updated = new Customer(saved.id(), "Updated", saved.lastName(), saved.email(), saved.dateOfBirth());
        Customer result = repository.save(updated);

        assertEquals(saved.id(), result.id());
        assertEquals(countBefore + 1, jpaRepository.count());
        assertEquals("Updated", repository.findById(saved.id()).get().firstName());
    }

    @Test
    void update_withUnchangedEmail_doesNotThrow() {
        long countBefore = jpaRepository.count();
        Customer created = Customer.create("Grace", "Hopper", "grace2@example.com", LocalDate.of(1906, 12, 9));
        Customer saved = repository.save(created);

        Customer updated = new Customer(saved.id(), saved.firstName(), "NewLastName", saved.email(), saved.dateOfBirth());

        assertDoesNotThrow(() -> repository.save(updated));
        assertEquals(countBefore + 1, jpaRepository.count());
    }
}
