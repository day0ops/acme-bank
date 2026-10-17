package io.acmebank.transactions.client;

import java.util.ArrayList;
import java.util.List;

/** Records every debit/credit call; lets tests configure specific calls to fail. */
public class FakeAccountsClient implements AccountsClient {

    public record Call(String operation, String accountId, long amountMinorUnits, String idempotencyKey) {}

    public final List<Call> calls = new ArrayList<>();
    private AccountsClientException creditFailure;
    private AccountsClientException debitFailure;

    /** The next {@link #debit} call throws {@code exception} instead of succeeding. */
    public void failNextDebitWith(AccountsClientException exception) {
        this.debitFailure = exception;
    }

    /** The next {@link #credit} call throws {@code exception} instead of succeeding. */
    public void failNextCreditWith(AccountsClientException exception) {
        this.creditFailure = exception;
    }

    @Override
    public void debit(String accountId, String currencyCode, long amountMinorUnits, String idempotencyKey) throws AccountsClientException {
        calls.add(new Call("debit", accountId, amountMinorUnits, idempotencyKey));
        if (debitFailure != null) {
            AccountsClientException toThrow = debitFailure;
            debitFailure = null;
            throw toThrow;
        }
    }

    @Override
    public void credit(String accountId, String currencyCode, long amountMinorUnits, String idempotencyKey) throws AccountsClientException {
        calls.add(new Call("credit", accountId, amountMinorUnits, idempotencyKey));
        if (creditFailure != null) {
            AccountsClientException toThrow = creditFailure;
            creditFailure = null;
            throw toThrow;
        }
    }
}
