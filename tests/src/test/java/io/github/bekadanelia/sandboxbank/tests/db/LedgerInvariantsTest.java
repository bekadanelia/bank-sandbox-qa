package io.github.bekadanelia.sandboxbank.tests.db;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.DepositRequest;
import io.github.bekadanelia.sandboxbank.api.model.TransferRequest;
import io.github.bekadanelia.sandboxbank.db.Database;
import io.github.bekadanelia.sandboxbank.db.LedgerInvariant;
import io.github.bekadanelia.sandboxbank.db.Row;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.DbTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.Smoke;
import io.github.bekadanelia.sandboxbank.support.Concurrently;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reconciliation of the books, the way a bank does it at the end of the day.
 *
 * <p>These checks do not care which customer did what. They look at every row in the database,
 * including the rows other tests have written, and ask whether the books still add up. The
 * class first runs a workload of its own so that the answer means something on an empty
 * database too.
 */
@DbTest
@Epic("Ledger")
@Feature("Reconciliation")
@Severity(SeverityLevel.BLOCKER)
class LedgerInvariantsTest {

    /**
     * The workload does not assert on single outcomes. Whatever the bank answers, the books must
     * balance afterwards. It only makes sure that enough money really moved for that to mean something.
     */
    @BeforeAll
    static void moveMoneyInEveryWayTheBankAllows() {
        CustomerSession nino = Customers.registered();
        CustomerSession giorgi = Customers.registered();
        Account lari = nino.accountWithBalance(Currency.GEL, "5000.00");
        Account dollars = nino.accountWithBalance(Currency.USD, "300.00");
        Account euros = nino.openAccount(Currency.EUR);
        Account giorgisLari = giorgi.accountWithBalance(Currency.GEL, "40.00");
        Account contested = giorgi.accountWithBalance(Currency.GEL, "25.00");
        List<Response> responses = new ArrayList<>();

        Allure.step("Transfers between customers and conversions with awkward rounding", () -> {
            responses.add(nino.sendTransfer(lari, giorgisLari, "120.50"));
            responses.add(nino.sendTransfer(lari, dollars, "10.02"));
            responses.add(nino.sendTransfer(dollars, euros, "3.00"));
            responses.add(nino.sendTransfer(euros, lari, "1.37"));
            responses.add(giorgi.sendTransfer(giorgisLari, dollars, "0.02"));
        });
        Allure.step("Transfers the bank must reject", () -> {
            responses.add(giorgi.sendTransfer(giorgisLari, lari, "9000.00"));
            responses.add(nino.sendTransfer(lari, dollars, "10000.01"));
            responses.add(nino.sendTransfer(lari, dollars, "0.01"));
            responses.add(nino.sendTransfer(euros, euros, "1.00"));
        });
        Allure.step("A request that is sent twice", () -> {
            String key = TestData.idempotencyKey();
            TransferRequest request = TransferRequest.of(lari, giorgisLari, "5.00");
            responses.add(nino.api().transfer(key, request));
            responses.add(nino.api().transfer(key, request));
        });
        Allure.step("Simultaneous attempts to spend the same money", () -> {
            responses.addAll(Concurrently.repeat(8, () -> giorgi.sendTransfer(contested, lari, "25.00")));
            responses.addAll(Concurrently.repeat(8, () -> nino.sendTransfer(lari, contested, "3.33")));
            responses.addAll(Concurrently.repeat(8, () -> giorgi.api()
                    .deposit(contested.id(), TestData.idempotencyKey(), DepositRequest.of("1.00"))));
        });

        assertThat(responses).filteredOn(response -> response.statusCode() == 201)
                .as("money movements that completed. With too few of them reconciliation proves nothing")
                .hasSizeGreaterThanOrEqualTo(15);
    }

    private static void assertHolds(LedgerInvariant invariant) {
        String sql = invariant.sql();
        Allure.attachment("Query that selects violations", sql);

        List<Row> violations = Database.reader().query(sql);

        if (!violations.isEmpty()) {
            Allure.attachment("Violating rows", "text/csv", Row.toCsv(violations));
        }
        assertThat(violations)
                .as("%s. Rows that break the rule:%n%s", invariant.rule(), Row.toCsv(violations))
                .isEmpty();
    }

    @Test
    @Smoke
    @Requirement("LED-01")
    void debits_equal_credits_in_every_transfer_and_currency() {
        assertHolds(LedgerInvariant.TRANSFER_ENTRIES_BALANCE);
    }

    @Test
    @Smoke
    @Requirement("LED-02")
    void every_account_balance_equals_the_sum_of_its_ledger_entries() {
        assertHolds(LedgerInvariant.BALANCES_MATCH_LEDGER);
    }

    @Test
    @Requirement("LED-03")
    void each_ledger_entry_continues_the_balance_the_previous_one_left() {
        assertHolds(LedgerInvariant.RUNNING_BALANCE_IS_CONTINUOUS);
    }

    @Test
    @Smoke
    @Requirement("LED-04")
    void all_balances_of_one_currency_add_up_to_zero() {
        assertHolds(LedgerInvariant.MONEY_IS_CONSERVED);
    }

    @Test
    @Requirement({"LED-05", "TRF-06"})
    void no_customer_account_is_or_ever_was_overdrawn() {
        assertHolds(LedgerInvariant.CUSTOMER_BALANCES_NEVER_NEGATIVE);
    }

    @Test
    @Requirement({"LED-06", "TRF-08"})
    void completed_transfers_have_their_ledger_entries_and_rejected_ones_have_none() {
        assertHolds(LedgerInvariant.LEDGER_MATCHES_TRANSFER_STATUS);
    }

    @Test
    @Requirement("LED-07")
    void no_transfer_is_left_pending() {
        assertHolds(LedgerInvariant.NO_TRANSFER_LEFT_PENDING);
    }

    @Test
    @Requirement("LED-08")
    void ledger_entries_match_the_accounts_and_amounts_of_their_transfer() {
        assertHolds(LedgerInvariant.ENTRIES_MATCH_TRANSFER);
    }

    @Test
    @Requirement("FX-02")
    void every_stored_conversion_equals_amount_times_rate_rounded_half_up() {
        assertHolds(LedgerInvariant.CONVERSIONS_USE_RATE_HALF_UP);
    }

    @Test
    @Requirement("AUD-01")
    void every_finished_transfer_has_exactly_one_audit_event() {
        assertHolds(LedgerInvariant.FINISHED_TRANSFERS_ARE_AUDITED);
    }

    @Test
    @Requirement("AUD-02")
    void no_audit_event_points_at_a_row_that_does_not_exist() {
        assertHolds(LedgerInvariant.AUDIT_EVENTS_ARE_NOT_ORPHANED);
    }

    @Test
    @Requirement("ACC-03")
    void every_stored_account_number_has_valid_check_digits() {
        assertHolds(LedgerInvariant.ACCOUNT_NUMBERS_ARE_VALID);
    }
}
