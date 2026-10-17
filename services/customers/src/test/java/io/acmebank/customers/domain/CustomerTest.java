package io.acmebank.customers.domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CustomerTest {

    @Test
    void create_withValidFields_succeeds() {
        Customer customer = Customer.create("Ada", "Lovelace", "ada@example.com", LocalDate.of(1990, 1, 15));

        assertEquals("Ada", customer.firstName());
        assertEquals("ada@example.com", customer.email());
    }

    @Test
    void create_withInvalidEmail_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            Customer.create("Ada", "Lovelace", "not-an-email", LocalDate.of(1990, 1, 15)));
    }

    @Test
    void create_withFutureDateOfBirth_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            Customer.create("Ada", "Lovelace", "ada@example.com", LocalDate.now().plusDays(1)));
    }
}
