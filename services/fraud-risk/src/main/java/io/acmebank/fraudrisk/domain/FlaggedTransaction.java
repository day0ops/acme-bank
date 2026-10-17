package io.acmebank.fraudrisk.domain;

/** A transaction flagged by a fraud/risk rule. Immutable; each flag is a new record, never updated. */
public record FlaggedTransaction(String id, String transactionId, String reason, long amountMinorUnits, String currencyCode) {

    /**
     * Creates a new, unpersisted flag record.
     *
     * @throws IllegalArgumentException if reason is null or blank
     */
    public static FlaggedTransaction flag(String transactionId, String reason, long amountMinorUnits, String currencyCode) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new FlaggedTransaction(null, transactionId, reason, amountMinorUnits, currencyCode);
    }
}
