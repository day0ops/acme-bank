package io.acmebank.transactions.persistence;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "transactions")
class TransactionEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(nullable = false)
    private short status;

    @Column(name = "failure_reason")
    private String failureReason;

    protected TransactionEntity() {}

    TransactionEntity(UUID fromAccountId, UUID toAccountId, String currencyCode, long amountMinor, short status, String failureReason) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.currencyCode = currencyCode;
        this.amountMinor = amountMinor;
        this.status = status;
        this.failureReason = failureReason;
    }

    UUID getId() { return id; }
    UUID getFromAccountId() { return fromAccountId; }
    UUID getToAccountId() { return toAccountId; }
    String getCurrencyCode() { return currencyCode; }
    long getAmountMinor() { return amountMinor; }
    short getStatus() { return status; }
    String getFailureReason() { return failureReason; }
    void setStatus(short status) { this.status = status; }
    void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
