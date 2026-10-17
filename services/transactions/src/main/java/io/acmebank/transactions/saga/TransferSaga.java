package io.acmebank.transactions.saga;

import io.acmebank.transactions.client.AccountsClient;
import io.acmebank.transactions.client.AccountsClientException;
import io.acmebank.transactions.domain.Transaction;
import io.acmebank.transactions.persistence.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Orchestrates a money transfer: debit the source account, credit the
 * destination, compensating (crediting the source back) if the credit step
 * fails. The terminal status change and its outbox event are written
 * together in one database transaction, via {@link TransferOutcomeRecorder}.
 */
@Component
public class TransferSaga {

    private static final Logger log = LoggerFactory.getLogger(TransferSaga.class);

    private final TransactionRepository transactionRepository;
    private final AccountsClient accountsClient;
    private final TransferOutcomeRecorder outcomeRecorder;

    public TransferSaga(TransactionRepository transactionRepository, AccountsClient accountsClient, TransferOutcomeRecorder outcomeRecorder) {
        this.transactionRepository = transactionRepository;
        this.accountsClient = accountsClient;
        this.outcomeRecorder = outcomeRecorder;
    }

    /**
     * Runs a transfer to completion: debits {@code fromAccountId}, then credits {@code toAccountId}.
     * If the credit fails after the debit succeeded, the debit is reversed by crediting
     * {@code fromAccountId} back before the transaction is marked failed. If that reversal
     * also fails, the transaction is still marked failed, with the failure reason noting
     * that manual reconciliation is required.
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
            return outcomeRecorder.markFailed(transaction, e.getMessage());
        }

        try {
            accountsClient.credit(toAccountId, currencyCode, amountMinorUnits, id + "-credit");
        } catch (AccountsClientException e) {
            boolean compensated = tryCompensate(fromAccountId, currencyCode, amountMinorUnits, id);
            String reason = compensated
                    ? e.getMessage()
                    : e.getMessage() + " (compensation also failed: account " + fromAccountId + " may be short funds, requires manual reconciliation)";
            return outcomeRecorder.markFailed(transaction, reason);
        }

        return outcomeRecorder.markCompleted(transaction);
    }

    /**
     * Attempts to reverse the debit by crediting {@code fromAccountId} back.
     *
     * @return true if the reversing credit succeeded, false if it also failed
     *         (requiring manual reconciliation, since the source account is
     *         now short funds with no automatic recovery)
     */
    private boolean tryCompensate(String fromAccountId, String currencyCode, long amountMinorUnits, String transactionId) {
        try {
            accountsClient.credit(fromAccountId, currencyCode, amountMinorUnits, transactionId + "-compensate");
            return true;
        } catch (AccountsClientException compensationFailure) {
            log.error("compensation failed for transaction {}: source account {} may be short funds, requires manual reconciliation",
                    transactionId, fromAccountId, compensationFailure);
            return false;
        }
    }
}
