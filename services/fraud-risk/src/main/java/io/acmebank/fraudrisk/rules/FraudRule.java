package io.acmebank.fraudrisk.rules;

import java.util.Optional;

/** Simple rule-based transaction scoring. A real system would have many rules; this demo has one. */
public class FraudRule {

    private static final long THRESHOLD_MINOR_UNITS = 1_000_000L;

    private FraudRule() {}

    /**
     * Evaluates a transfer amount against the fraud/risk rule set.
     *
     * @return a human-readable flag reason if the rule trips, empty otherwise
     */
    public static Optional<String> evaluate(long amountMinorUnits, String currencyCode) {
        if (amountMinorUnits >= THRESHOLD_MINOR_UNITS) {
            return Optional.of("transfer amount " + amountMinorUnits + " " + currencyCode + " meets or exceeds the single-transfer threshold");
        }
        return Optional.empty();
    }
}
