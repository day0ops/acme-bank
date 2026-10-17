package io.acmebank.customers.rest;

import io.acmebank.customers.domain.Customer;
import io.acmebank.customers.persistence.CustomerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** REST endpoint for customers; delegates persistence to {@link CustomerRepository}, the gRPC layer's sibling. */
@RestController
@RequestMapping("/customers")
class CustomerController {

    private final CustomerRepository repository;

    CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    record CreateCustomerBody(String firstName, String lastName, String email, String dateOfBirth) {}
    record CustomerResponse(String id, String firstName, String lastName, String email, String dateOfBirth) {
        static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.id(), c.firstName(), c.lastName(), c.email(), c.dateOfBirth().toString());
        }
    }

    @PostMapping
    ResponseEntity<CustomerResponse> create(@RequestBody CreateCustomerBody body) {
        Customer customer = Customer.create(body.firstName(), body.lastName(), body.email(), parseDateOfBirth(body.dateOfBirth()));
        Customer saved = repository.save(customer);
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerResponse.from(saved));
    }

    @GetMapping("/{id}")
    CustomerResponse get(@PathVariable String id) {
        return repository.findById(id).map(CustomerResponse::from)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @PutMapping("/{id}")
    CustomerResponse update(@PathVariable String id, @RequestBody CreateCustomerBody body) {
        Customer existing = repository.findById(id).orElseThrow(() -> new CustomerNotFoundException(id));
        Customer updated = new Customer(existing.id(), body.firstName(), body.lastName(), body.email(), existing.dateOfBirth());
        return CustomerResponse.from(repository.save(updated));
    }

    // LocalDate.parse throws the lower-level DateTimeParseException; reraised as IllegalArgumentException
    // so malformed input is reported as a 400 problem+json like the rest of the validation in Customer.create.
    private static LocalDate parseDateOfBirth(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("dateOfBirth must be a valid ISO-8601 date: " + value);
        }
    }
}
