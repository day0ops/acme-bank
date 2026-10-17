package io.acmebank.transactions.domain;

/** A money transfer between two accounts. Immutable; state transitions return new instances. */
public record Transaction(
        String id,
        String fromAccountId,
        String toAccountId,
        String currencyCode,
        long amountMinorUnits,
        TransactionStatus status,
        String failureReason) {

    /**
     * Starts a new transfer in {@link TransactionStatus#PENDING}, with no id (not yet persisted).
     *
     * @throws IllegalArgumentException if amountMinorUnits is not positive, or fromAccountId equals toAccountId
     */
    public static Transaction initiate(String fromAccountId, String toAccountId, String currencyCode, long amountMinorUnits) {
        if (amountMinorUnits <= 0) {
            throw new IllegalArgumentException("amountMinorUnits must be positive: " + amountMinorUnits);
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("fromAccountId and toAccountId must differ");
        }
        return new Transaction(null, fromAccountId, toAccountId, currencyCode, amountMinorUnits, TransactionStatus.PENDING, null);
    }

    /** Returns a copy transitioned to {@link TransactionStatus#COMPLETED}. */
    public Transaction complete() {
        return new Transaction(id, fromAccountId, toAccountId, currencyCode, amountMinorUnits, TransactionStatus.COMPLETED, null);
    }

    /**
     * Returns a copy transitioned to {@link TransactionStatus#FAILED}.
     *
     * @param reason human-readable failure reason, stored alongside the transaction
     */
    public Transaction fail(String reason) {
        return new Transaction(id, fromAccountId, toAccountId, currencyCode, amountMinorUnits, TransactionStatus.FAILED, reason);
    }

    /** Returns a copy with the given persisted id. */
    public Transaction withId(String id) {
        return new Transaction(id, fromAccountId, toAccountId, currencyCode, amountMinorUnits, status, failureReason);
    }
}
