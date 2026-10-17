package io.acmebank.transactions.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Records every debit/credit call; lets tests configure specific calls to fail. */
public class FakeAccountsClient implements AccountsClient {

    public record Call(String operation, String accountId, long amountMinorUnits, String idempotencyKey) {}

    public final List<Call> calls = new ArrayList<>();
    private final Deque<AccountsClientException> creditFailures = new ArrayDeque<>();
    private AccountsClientException debitFailure;

    /** The next {@link #debit} call throws {@code exception} instead of succeeding. */
    public void failNextDebitWith(AccountsClientException exception) {
        this.debitFailure = exception;
    }

    /**
     * The next {@link #credit} call throws {@code exception} instead of succeeding. Can be called
     * repeatedly to queue up failures for several consecutive credit calls in a row (e.g. the
     * original credit and a subsequent compensating credit).
     */
    public void failNextCreditWith(AccountsClientException exception) {
        creditFailures.addLast(exception);
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
        AccountsClientException toThrow = creditFailures.pollFirst();
        if (toThrow != null) {
            throw toThrow;
        }
    }
}
