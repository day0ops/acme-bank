package io.acmebank.transactions.grpc;

import io.acmebank.proto.common.v1.Money;
import io.acmebank.proto.transactions.v1.*;
import io.acmebank.transactions.domain.Transaction;
import io.acmebank.transactions.persistence.TransactionRepository;
import io.acmebank.transactions.saga.TransferSaga;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** gRPC endpoint for {@code TransactionsService}; delegates transfers to {@link TransferSaga} and reads to {@link TransactionRepository}. */
@Component
public class TransactionsGrpcService extends TransactionsServiceGrpc.TransactionsServiceImplBase {

    private final TransferSaga saga;
    private final TransactionRepository repository;

    public TransactionsGrpcService(TransferSaga saga, TransactionRepository repository) {
        this.saga = saga;
        this.repository = repository;
    }

    @Override
    public void transfer(TransferRequest request, StreamObserver<TransferResponse> responseObserver) {
        Transaction result = saga.execute(
                request.getFromAccountId(), request.getToAccountId(),
                request.getAmount().getCurrencyCode(), request.getAmount().getMinorUnits());
        responseObserver.onNext(TransferResponse.newBuilder().setTransaction(toProto(result)).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getTransaction(GetTransactionRequest request, StreamObserver<GetTransactionResponse> responseObserver) {
        Optional<Transaction> found = repository.findById(request.getId());
        if (found.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("transaction " + request.getId() + " not found").asRuntimeException());
            return;
        }
        responseObserver.onNext(GetTransactionResponse.newBuilder().setTransaction(toProto(found.get())).build());
        responseObserver.onCompleted();
    }

    @Override
    public void listTransactionsByAccount(ListTransactionsByAccountRequest request, StreamObserver<ListTransactionsByAccountResponse> responseObserver) {
        var builder = ListTransactionsByAccountResponse.newBuilder();
        repository.findByAccountId(request.getAccountId()).forEach(tx -> builder.addTransactions(toProto(tx)));
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private static io.acmebank.proto.transactions.v1.Transaction toProto(Transaction tx) {
        var builder = io.acmebank.proto.transactions.v1.Transaction.newBuilder()
                .setId(tx.id())
                .setFromAccountId(tx.fromAccountId())
                .setToAccountId(tx.toAccountId())
                .setAmount(Money.newBuilder().setCurrencyCode(tx.currencyCode()).setMinorUnits(tx.amountMinorUnits()).build())
                .setStatus(toProtoStatus(tx.status()));
        if (tx.failureReason() != null) {
            builder.setFailureReason(tx.failureReason());
        }
        return builder.build();
    }

    private static TransactionStatus toProtoStatus(io.acmebank.transactions.domain.TransactionStatus status) {
        return switch (status) {
            case PENDING -> TransactionStatus.TRANSACTION_STATUS_PENDING;
            case COMPLETED -> TransactionStatus.TRANSACTION_STATUS_COMPLETED;
            case FAILED -> TransactionStatus.TRANSACTION_STATUS_FAILED;
        };
    }
}
