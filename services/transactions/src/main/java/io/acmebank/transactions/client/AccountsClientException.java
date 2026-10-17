package io.acmebank.transactions.client;

/** Thrown when a call to the Accounts service fails. */
public class AccountsClientException extends Exception {

    private final boolean insufficientFunds;

    /**
     * @param message human-readable failure detail
     * @param insufficientFunds true if the account legitimately can't cover the amount,
     *                          false if the call failed for an infra reason
     */
    public AccountsClientException(String message, boolean insufficientFunds) {
        super(message);
        this.insufficientFunds = insufficientFunds;
    }

    /** @return true if the failure is due to insufficient funds rather than an infra error */
    public boolean isInsufficientFunds() {
        return insufficientFunds;
    }
}
