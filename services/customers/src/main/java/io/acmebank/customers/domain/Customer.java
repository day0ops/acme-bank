package io.acmebank.customers.domain;

import java.time.LocalDate;

/** A customer profile. Immutable; use {@link #create} to validate inputs at construction. */
public record Customer(String id, String firstName, String lastName, String email, LocalDate dateOfBirth) {

    public static Customer create(String firstName, String lastName, String email, LocalDate dateOfBirth) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("email must be a valid address: " + email);
        }
        if (dateOfBirth == null || dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("dateOfBirth must be in the past: " + dateOfBirth);
        }
        return new Customer(null, firstName, lastName, email, dateOfBirth);
    }

    public Customer withId(String id) {
        return new Customer(id, firstName, lastName, email, dateOfBirth);
    }
}
