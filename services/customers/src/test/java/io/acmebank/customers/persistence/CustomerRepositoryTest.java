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
}
