package io.acmebank.customers.persistence;

import io.acmebank.customers.domain.Customer;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class CustomerRepository {

    private final CustomerJpaRepository jpaRepository;

    public CustomerRepository(CustomerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Inserts a new customer when {@code customer.id()} is null; otherwise loads and mutates
     * the managed entity so the update persists in place instead of inserting a duplicate row.
     *
     * @param customer the customer to insert or update
     * @return the persisted customer, including its id
     * @throws IllegalArgumentException if {@code customer.id()} is set but no such customer exists
     */
    public Customer save(Customer customer) {
        if (customer.id() == null) {
            CustomerEntity entity = new CustomerEntity(
                    customer.firstName(), customer.lastName(), customer.email(), customer.dateOfBirth());
            CustomerEntity saved = jpaRepository.save(entity);
            return toDomain(saved);
        }

        CustomerEntity entity = jpaRepository.findById(UUID.fromString(customer.id()))
                .orElseThrow(() -> new IllegalArgumentException("customer " + customer.id() + " not found"));
        entity.setFirstName(customer.firstName());
        entity.setLastName(customer.lastName());
        entity.setEmail(customer.email());
        CustomerEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    public Optional<Customer> findById(String id) {
        try {
            return jpaRepository.findById(UUID.fromString(id)).map(CustomerRepository::toDomain);
        } catch (IllegalArgumentException malformedUuid) {
            return Optional.empty();
        }
    }

    private static Customer toDomain(CustomerEntity entity) {
        return new Customer(
                entity.getId().toString(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getEmail(),
                entity.getDateOfBirth());
    }
}
