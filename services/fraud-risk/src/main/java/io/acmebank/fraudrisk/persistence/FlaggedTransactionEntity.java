package io.acmebank.fraudrisk.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "flagged_transactions")
class FlaggedTransactionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(nullable = false)
    private String reason;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    protected FlaggedTransactionEntity() {}

    FlaggedTransactionEntity(UUID transactionId, String reason, String currencyCode, long amountMinor) {
        this.transactionId = transactionId;
        this.reason = reason;
        this.currencyCode = currencyCode;
        this.amountMinor = amountMinor;
    }

    UUID getId() { return id; }
    UUID getTransactionId() { return transactionId; }
    String getReason() { return reason; }
    String getCurrencyCode() { return currencyCode; }
    long getAmountMinor() { return amountMinor; }
}
