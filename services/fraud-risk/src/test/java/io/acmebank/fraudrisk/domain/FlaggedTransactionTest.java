package io.acmebank.fraudrisk.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlaggedTransactionTest {

    @Test
    void flag_withValidFields_succeeds() {
        FlaggedTransaction flagged = FlaggedTransaction.flag("tx-1", "amount exceeds threshold", 1_500_000L, "USD");

        assertEquals("tx-1", flagged.transactionId());
        assertEquals("amount exceeds threshold", flagged.reason());
    }

    @Test
    void flag_withBlankReason_throws() {
        assertThrows(IllegalArgumentException.class, () -> FlaggedTransaction.flag("tx-1", "  ", 1_500_000L, "USD"));
    }
}
