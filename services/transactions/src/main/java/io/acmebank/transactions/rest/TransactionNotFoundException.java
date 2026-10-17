package io.acmebank.transactions.rest;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String id) {
        super("transaction " + id + " not found");
    }
}
