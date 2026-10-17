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

    public Customer save(Customer customer) {
        CustomerEntity entity = new CustomerEntity(
                customer.firstName(), customer.lastName(), customer.email(), customer.dateOfBirth());
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
