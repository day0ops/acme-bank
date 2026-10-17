package io.acmebank.customers.domain;

import java.time.LocalDate;

/** A customer profile. Immutable; use {@link #create} to validate inputs at construction. */
public record Customer(String id, String firstName, String lastName, String email, LocalDate dateOfBirth) {

    /**
     * Creates a new customer with no id (not yet persisted), validating the given fields.
     *
     * @param firstName   given name
     * @param lastName    family name
     * @param email       must contain {@code @}
     * @param dateOfBirth must be in the past
     * @return a new {@code Customer} with {@code id() == null}
     * @throws IllegalArgumentException if email or dateOfBirth is invalid
     */
    public static Customer create(String firstName, String lastName, String email, LocalDate dateOfBirth) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("email must be a valid address: " + email);
        }
        if (dateOfBirth == null || dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("dateOfBirth must be in the past: " + dateOfBirth);
        }
        return new Customer(null, firstName, lastName, email, dateOfBirth);
    }

    /**
     * Returns a copy of this customer with the given id, leaving all other fields unchanged.
     *
     * @param id the persisted identifier to assign
     * @return a new {@code Customer} with the given id
     */
    public Customer withId(String id) {
        return new Customer(id, firstName, lastName, email, dateOfBirth);
    }
}
