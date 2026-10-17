package io.acmebank.fraudrisk.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FraudRuleTest {

    @Test
    void evaluate_belowThreshold_returnsEmpty() {
        assertTrue(FraudRule.evaluate(999_999L, "USD").isEmpty());
    }

    @Test
    void evaluate_atThreshold_flags() {
        assertTrue(FraudRule.evaluate(1_000_000L, "USD").isPresent());
    }

    @Test
    void evaluate_aboveThreshold_flagsWithReason() {
        var reason = FraudRule.evaluate(5_000_000L, "USD");
        assertTrue(reason.isPresent());
        assertTrue(reason.get().toLowerCase().contains("threshold"));
    }
}
