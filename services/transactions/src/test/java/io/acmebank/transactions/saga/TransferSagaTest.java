package io.acmebank.transactions.saga;

import io.acmebank.transactions.client.AccountsClientException;
import io.acmebank.transactions.client.FakeAccountsClient;
import io.acmebank.transactions.domain.TransactionStatus;
import io.acmebank.transactions.persistence.OutboxRepository;
import io.acmebank.transactions.persistence.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class TransferSagaTest {

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
    private OutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void execute_withBothStepsSucceeding_completesAndWritesOutboxEvent() throws Exception {
        FakeAccountsClient fakeClient = new FakeAccountsClient();
        TransferSaga saga = new TransferSaga(transactionRepository, outboxRepository, fakeClient);

        var result = saga.execute("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222", "USD", 5000L);

        assertEquals(TransactionStatus.COMPLETED, result.status());
        assertEquals(2, fakeClient.calls.size());
        assertEquals("debit", fakeClient.calls.get(0).operation());
        assertEquals("credit", fakeClient.calls.get(1).operation());

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?::uuid AND event_type = 'TransactionCompleted'",
                Integer.class, result.id());
        assertEquals(1, eventCount);
    }

    @Test
    void execute_withDebitFailing_marksFailedAndNeverCallsCredit() throws Exception {
        FakeAccountsClient fakeClient = new FakeAccountsClient();
        fakeClient.failNextDebitWith(new AccountsClientException("insufficient funds", true));
        TransferSaga saga = new TransferSaga(transactionRepository, outboxRepository, fakeClient);

        var result = saga.execute("33333333-3333-3333-3333-333333333333", "44444444-4444-4444-4444-444444444444", "USD", 5000L);

        assertEquals(TransactionStatus.FAILED, result.status());
        assertEquals("insufficient funds", result.failureReason());
        assertEquals(1, fakeClient.calls.size()); // only the debit attempt, no credit
    }

    @Test
    void execute_withCreditFailing_compensatesWithReversingCreditAndMarksFailed() throws Exception {
        FakeAccountsClient fakeClient = new FakeAccountsClient();
        fakeClient.failNextCreditWith(new AccountsClientException("destination account frozen", false));
        TransferSaga saga = new TransferSaga(transactionRepository, outboxRepository, fakeClient);

        String fromAccount = "55555555-5555-5555-5555-555555555555";
        var result = saga.execute(fromAccount, "66666666-6666-6666-6666-666666666666", "USD", 5000L);

        assertEquals(TransactionStatus.FAILED, result.status());
        // debit, credit (fails), compensating credit back to fromAccount
        assertEquals(3, fakeClient.calls.size());
        assertEquals("debit", fakeClient.calls.get(0).operation());
        assertEquals("credit", fakeClient.calls.get(1).operation());
        assertEquals("credit", fakeClient.calls.get(2).operation());
        assertEquals(fromAccount, fakeClient.calls.get(2).accountId()); // compensation credits the SOURCE account
        assertEquals(5000L, fakeClient.calls.get(2).amountMinorUnits());
    }
}
