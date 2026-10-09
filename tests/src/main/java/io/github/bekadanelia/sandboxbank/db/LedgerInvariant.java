package io.github.bekadanelia.sandboxbank.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Rules that must hold for the books at any moment, whatever the customers did.
 *
 * <p>Each rule is one SQL file that selects the rows breaking it, so an empty result means the
 * rule holds. A single statement sees a single snapshot of the data, which makes these checks
 * safe to run while other tests are moving money.
 */
public enum LedgerInvariant {

    TRANSFER_ENTRIES_BALANCE("Debits equal credits in every transfer and currency"),
    BALANCES_MATCH_LEDGER("Every account balance equals the sum of its ledger entries"),
    RUNNING_BALANCE_IS_CONTINUOUS("Each entry continues the balance the previous entry left"),
    MONEY_IS_CONSERVED("All balances of one currency add up to zero"),
    CUSTOMER_BALANCES_NEVER_NEGATIVE("No customer account is or ever was overdrawn"),
    LEDGER_MATCHES_TRANSFER_STATUS("Completed transfers have their ledger entries, rejected ones have none"),
    NO_TRANSFER_LEFT_PENDING("No transfer is left in a pending state"),
    ENTRIES_MATCH_TRANSFER("Entries are posted to the accounts and amounts the transfer names"),
    CONVERSIONS_USE_RATE_HALF_UP("Converted amounts equal the amount times the rate, rounded half up"),
    FINISHED_TRANSFERS_ARE_AUDITED("Every finished transfer has exactly one audit event"),
    AUDIT_EVENTS_ARE_NOT_ORPHANED("Every audit event points at an existing row"),
    ACCOUNT_NUMBERS_ARE_VALID("Every account number has valid ISO 13616 check digits");

    private final String rule;

    LedgerInvariant(String rule) {
        this.rule = rule;
    }

    /** The rule in one sentence, for assertion messages and reports. */
    public String rule() {
        return rule;
    }

    /** The query that selects the rows breaking this rule. */
    public String sql() {
        String file = "/sql/invariants/" + name().toLowerCase(Locale.ROOT) + ".sql";
        try (InputStream resource = LedgerInvariant.class.getResourceAsStream(file)) {
            if (resource == null) {
                throw new IllegalStateException("Missing SQL file " + file);
            }
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
