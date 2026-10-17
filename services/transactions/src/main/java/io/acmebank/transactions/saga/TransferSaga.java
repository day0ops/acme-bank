package io.acmebank.transactions.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.acmebank.transactions.client.AccountsClient;
import io.acmebank.transactions.client.AccountsClientException;
import io.acmebank.transactions.domain.Transaction;
import io.acmebank.transactions.persistence.OutboxRepository;
import io.acmebank.transactions.persistence.TransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Orchestrates a money transfer: debit the source account, credit the
 * destination, compensating (crediting the source back) if the credit step
 * fails. The terminal status change and its outbox event are written
 * together in one database transaction.
 */
@Component
public class TransferSaga {

    private final TransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final AccountsClient accountsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TransferSaga(TransactionRepository transactionRepository, OutboxRepository outboxRepository, AccountsClient accountsClient) {
        this.transactionRepository = transactionRepository;
        this.outboxRepository = outboxRepository;
        this.accountsClient = accountsClient;
    }

    /**
     * Runs a transfer to completion: debits {@code fromAccountId}, then credits {@code toAccountId}.
     * If the credit fails after the debit succeeded, the debit is reversed by crediting
     * {@code fromAccountId} back before the transaction is marked failed.
     *
     * @param fromAccountId      account to debit
     * @param toAccountId        account to credit
     * @param currencyCode       ISO 4217 currency code of the amount
     * @param amountMinorUnits   amount in the currency's minor unit (e.g. cents)
     * @return the persisted transaction in its terminal {@code COMPLETED} or {@code FAILED} state
     */
    public Transaction execute(String fromAccountId, String toAccountId, String currencyCode, long amountMinorUnits) {
        Transaction transaction = transactionRepository.save(Transaction.initiate(fromAccountId, toAccountId, currencyCode, amountMinorUnits));
        String id = transaction.id();

        try {
            accountsClient.debit(fromAccountId, currencyCode, amountMinorUnits, id + "-debit");
        } catch (AccountsClientException e) {
            return markFailed(transaction, e.getMessage());
        }

        try {
            accountsClient.credit(toAccountId, currencyCode, amountMinorUnits, id + "-credit");
        } catch (AccountsClientException e) {
            compensate(fromAccountId, currencyCode, amountMinorUnits, id);
            return markFailed(transaction, e.getMessage());
        }

        return markCompleted(transaction);
    }

    private void compensate(String fromAccountId, String currencyCode, long amountMinorUnits, String transactionId) {
        try {
            accountsClient.credit(fromAccountId, currencyCode, amountMinorUnits, transactionId + "-compensate");
        } catch (AccountsClientException compensationFailure) {
            // The compensation itself failing means the source account is now short funds with
            // no automatic recovery. This requires manual/operational intervention. Logged, not
            // silently swallowed, but there is no further automated remediation this saga can take.
            throw new IllegalStateException("compensation failed for transaction " + transactionId, compensationFailure);
        }
    }

    @Transactional
    private Transaction markCompleted(Transaction transaction) {
        Transaction completed = transactionRepository.save(transaction.complete());
        outboxRepository.append("Transaction", completed.id(), "TransactionCompleted", toJson(completed));
        return completed;
    }

    @Transactional
    private Transaction markFailed(Transaction transaction, String reason) {
        Transaction failed = transactionRepository.save(transaction.fail(reason));
        outboxRepository.append("Transaction", failed.id(), "TransactionFailed", toJson(failed));
        return failed;
    }

    private String toJson(Transaction transaction) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "id", transaction.id(),
                    "fromAccountId", transaction.fromAccountId(),
                    "toAccountId", transaction.toAccountId(),
                    "status", transaction.status().toString()));
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize outbox event payload", e);
        }
    }
}
