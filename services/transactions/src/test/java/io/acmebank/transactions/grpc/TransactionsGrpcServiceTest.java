package io.acmebank.transactions.grpc;

import io.acmebank.transactions.client.FakeAccountsClient;
import io.acmebank.transactions.persistence.OutboxRepository;
import io.acmebank.transactions.persistence.TransactionRepository;
import io.acmebank.transactions.saga.TransferSaga;
import io.acmebank.proto.common.v1.Money;
import io.acmebank.proto.transactions.v1.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

// grpc.port=0 binds an OS-assigned ephemeral port so each @SpringBootTest context (cached
// independently per test class because of @DynamicPropertySource) can start its own
// GrpcServerLifecycle without port 9090 colliding across concurrently cached contexts.
@SpringBootTest(properties = "grpc.port=0")
@Testcontainers
class TransactionsGrpcServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transactions").withUsername("transactions").withPassword("transactions");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private io.acmebank.transactions.saga.TransferOutcomeRecorder outcomeRecorder;

    @Test
    void transfer_withSuccessfulSaga_returnsCompletedTransaction() {
        TransferSaga saga = new TransferSaga(transactionRepository, new FakeAccountsClient(), outcomeRecorder);
        TransactionsGrpcService service = new TransactionsGrpcService(saga, transactionRepository);

        var responseObserver = new io.grpc.stub.StreamObserver<TransferResponse>() {
            TransferResponse result;
            @Override public void onNext(TransferResponse value) { result = value; }
            @Override public void onError(Throwable t) { fail(t); }
            @Override public void onCompleted() {}
        };

        service.transfer(TransferRequest.newBuilder()
                .setFromAccountId("77777777-7777-7777-7777-777777777777")
                .setToAccountId("88888888-8888-8888-8888-888888888888")
                .setAmount(Money.newBuilder().setCurrencyCode("USD").setMinorUnits(2500).build())
                .build(), responseObserver);

        assertEquals(TransactionStatus.TRANSACTION_STATUS_COMPLETED, responseObserver.result.getTransaction().getStatus());
    }

    @Test
    void transfer_withSameFromAndToAccount_returnsInvalidArgument() {
        TransferSaga saga = new TransferSaga(transactionRepository, new FakeAccountsClient(), outcomeRecorder);
        TransactionsGrpcService service = new TransactionsGrpcService(saga, transactionRepository);

        var responseObserver = new io.grpc.stub.StreamObserver<TransferResponse>() {
            Throwable error;
            @Override public void onNext(TransferResponse value) { fail("expected onError, got a result"); }
            @Override public void onError(Throwable t) { error = t; }
            @Override public void onCompleted() {}
        };

        service.transfer(TransferRequest.newBuilder()
                .setFromAccountId("99999999-9999-9999-9999-999999999999")
                .setToAccountId("99999999-9999-9999-9999-999999999999")
                .setAmount(Money.newBuilder().setCurrencyCode("USD").setMinorUnits(2500).build())
                .build(), responseObserver);

        assertNotNull(responseObserver.error);
        assertEquals(io.grpc.Status.Code.INVALID_ARGUMENT, io.grpc.Status.fromThrowable(responseObserver.error).getCode());
    }
}
