package io.acmebank.transactions.client;

import io.acmebank.proto.accounts.v1.AccountsServiceGrpc;
import io.acmebank.proto.accounts.v1.CreditRequest;
import io.acmebank.proto.accounts.v1.DebitRequest;
import io.acmebank.proto.common.v1.Money;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Component;

/** Calls the Accounts service's Debit/Credit RPCs over gRPC. */
@Component
public class GrpcAccountsClient implements AccountsClient {

    private final AccountsServiceGrpc.AccountsServiceBlockingStub stub;

    public GrpcAccountsClient(AccountsServiceGrpc.AccountsServiceBlockingStub stub) {
        this.stub = stub;
    }

    @Override
    public void debit(String accountId, String currencyCode, long amountMinorUnits, String idempotencyKey) throws AccountsClientException {
        try {
            stub.debit(DebitRequest.newBuilder()
                    .setAccountId(accountId)
                    .setAmount(Money.newBuilder().setCurrencyCode(currencyCode).setMinorUnits(amountMinorUnits).build())
                    .setIdempotencyKey(idempotencyKey)
                    .build());
        } catch (StatusRuntimeException e) {
            throw toClientException(e);
        }
    }

    @Override
    public void credit(String accountId, String currencyCode, long amountMinorUnits, String idempotencyKey) throws AccountsClientException {
        try {
            stub.credit(CreditRequest.newBuilder()
                    .setAccountId(accountId)
                    .setAmount(Money.newBuilder().setCurrencyCode(currencyCode).setMinorUnits(amountMinorUnits).build())
                    .setIdempotencyKey(idempotencyKey)
                    .build());
        } catch (StatusRuntimeException e) {
            throw toClientException(e);
        }
    }

    // FAILED_PRECONDITION is the Accounts service's status code for "insufficient funds";
    // every other code is treated as an infra failure.
    private AccountsClientException toClientException(StatusRuntimeException e) {
        boolean insufficientFunds = e.getStatus().getCode() == Status.Code.FAILED_PRECONDITION;
        return new AccountsClientException(e.getMessage(), insufficientFunds);
    }
}
