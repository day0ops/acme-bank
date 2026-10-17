package io.acmebank.transactions.client;

/** A client for the Accounts service's Debit/Credit operations, used by the transfer saga. */
public interface AccountsClient {

    /**
     * Debits an account, failing idempotently if {@code idempotencyKey} has already been applied.
     *
     * @param accountId          the account to debit
     * @param currencyCode       ISO 4217 currency code of the amount
     * @param amountMinorUnits   amount in the currency's minor unit (e.g. cents)
     * @param idempotencyKey     key identifying this debit attempt, for safe retries
     * @throws AccountsClientException if the account can't cover the amount or the call fails
     */
    void debit(String accountId, String currencyCode, long amountMinorUnits, String idempotencyKey) throws AccountsClientException;

    /**
     * Credits an account, failing idempotently if {@code idempotencyKey} has already been applied.
     *
     * @param accountId          the account to credit
     * @param currencyCode       ISO 4217 currency code of the amount
     * @param amountMinorUnits   amount in the currency's minor unit (e.g. cents)
     * @param idempotencyKey     key identifying this credit attempt, for safe retries
     * @throws AccountsClientException if the call fails
     */
    void credit(String accountId, String currencyCode, long amountMinorUnits, String idempotencyKey) throws AccountsClientException;
}
