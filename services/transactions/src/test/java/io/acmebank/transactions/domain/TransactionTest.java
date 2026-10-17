package io.acmebank.transactions.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    @Test
    void initiate_withValidFields_startsAsPending() {
        Transaction tx = Transaction.initiate("acct-a", "acct-b", "USD", 1000L);

        assertEquals(TransactionStatus.PENDING, tx.status());
        assertEquals(1000L, tx.amountMinorUnits());
    }

    @Test
    void initiate_withZeroAmount_throws() {
        assertThrows(IllegalArgumentException.class, () -> Transaction.initiate("acct-a", "acct-b", "USD", 0L));
    }

    @Test
    void initiate_withSameFromAndToAccount_throws() {
        assertThrows(IllegalArgumentException.class, () -> Transaction.initiate("acct-a", "acct-a", "USD", 1000L));
    }

    @Test
    void complete_transitionsToCompleted() {
        Transaction tx = Transaction.initiate("acct-a", "acct-b", "USD", 1000L);
        Transaction completed = tx.complete();

        assertEquals(TransactionStatus.COMPLETED, completed.status());
        assertEquals(TransactionStatus.PENDING, tx.status()); // original unchanged (immutable)
    }

    @Test
    void fail_transitionsToFailedWithReason() {
        Transaction tx = Transaction.initiate("acct-a", "acct-b", "USD", 1000L);
        Transaction failed = tx.fail("insufficient funds");

        assertEquals(TransactionStatus.FAILED, failed.status());
        assertEquals("insufficient funds", failed.failureReason());
    }
}
