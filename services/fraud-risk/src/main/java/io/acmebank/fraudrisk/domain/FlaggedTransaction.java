package io.acmebank.fraudrisk.domain;

/** A transaction flagged by a fraud/risk rule. Immutable; each flag is a new record, never updated. */
public record FlaggedTransaction(String id, String transactionId, String reason, long amountMinorUnits, String currencyCode) {

    /**
     * Creates a new, unpersisted flag record.
     *
     * @param transactionId    the transaction being flagged
     * @param reason           human-readable reason the rule tripped; must not be blank
     * @param amountMinorUnits the flagged transaction's amount, in minor units
     * @param currencyCode     the flagged transaction's ISO 4217 currency code
     * @return a new {@code FlaggedTransaction} with {@code id() == null}
     * @throws IllegalArgumentException if reason is null or blank
     */
    public static FlaggedTransaction flag(String transactionId, String reason, long amountMinorUnits, String currencyCode) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new FlaggedTransaction(null, transactionId, reason, amountMinorUnits, currencyCode);
    }
}
