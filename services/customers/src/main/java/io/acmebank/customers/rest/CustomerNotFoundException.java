package io.acmebank.customers.rest;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String id) {
        super("customer " + id + " not found");
    }
}
