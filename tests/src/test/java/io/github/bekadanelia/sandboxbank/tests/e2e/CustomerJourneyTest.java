package io.github.bekadanelia.sandboxbank.tests.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.microsoft.playwright.Page;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.RegistrationRequest;
import io.github.bekadanelia.sandboxbank.api.model.Transaction;
import io.github.bekadanelia.sandboxbank.db.Database;
import io.github.bekadanelia.sandboxbank.db.Row;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.E2eTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.Smoke;
import io.github.bekadanelia.sandboxbank.ui.pages.DashboardPage;
import io.github.bekadanelia.sandboxbank.ui.pages.LoginPage;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A few journeys that cross every layer. The customer acts in the browser, and the result is
 * then checked where it matters: through the API another client would use and in the ledger.
 *
 * <p>There are only two of these on purpose. Journeys are slow and fail for many reasons, so
 * rules are tested at the lowest layer that can prove them and journeys only prove the wiring.
 */
@E2eTest
@Epic("Journeys")
@Feature("Customer journeys")
class CustomerJourneyTest {

    @Test
    @Smoke
    @Requirement({"AUTH-01", "ACC-01", "DEP-01", "FX-01", "LED-01", "AUD-01"})
    void a_new_customer_registers_funds_an_account_and_converts_currency(Page page) {
        RegistrationRequest registration = new RegistrationRequest(
                TestData.uniqueEmail(), "Nino Beridze", TestData.PASSWORD);

        DashboardPage dashboard = LoginPage.open(page).createAccount().register(registration);
        dashboard.openAccount(Currency.GEL);
        assertThat(dashboard.accountRows()).hasCount(1);
        dashboard.openAccount(Currency.USD);
        assertThat(dashboard.accountRows()).hasCount(2);

        CustomerSession viaApi = Customers.signedIn(registration.email(), registration.password());
        Account lari = viaApi.accounts().get(0);
        Account dollars = viaApi.accounts().get(1);

        dashboard.addFundsForm().add(lari, "500.00");
        assertThat(dashboard.notice()).hasText("Funds added. 500.00 GEL received.");
        dashboard.transferForm().fill(lari, dollars.number(), "100.00").describe("Holiday budget").send();
        assertThat(dashboard.notice())
                .hasText("Transfer completed. 100.00 GEL sent, 37.00 USD received at rate 0.370000.");
        assertThat(dashboard.account(lari).balance()).hasText("400.00");
        assertThat(dashboard.account(dollars).balance()).hasText("37.00");

        Transaction received = Allure.step("The API reports the same balances and the incoming entry", () -> {
            Assertions.assertThat(viaApi.balanceOf(lari)).isEqualByComparingTo("400.00");
            Assertions.assertThat(viaApi.balanceOf(dollars)).isEqualByComparingTo("37.00");
            Transaction newest = viaApi.statement(dollars).items().getFirst();
            Assertions.assertThat(newest.amount()).isEqualByComparingTo("37.00");
            Assertions.assertThat(newest.description()).isEqualTo("Holiday budget");
            return newest;
        });

        Allure.step("The ledger holds a balanced conversion and one audit event", () -> {
            Database db = Database.reader();
            List<Row> entries = db.query("""
                    SELECT a.kind, e.direction, e.amount, e.currency
                    FROM ledger_entries e
                             JOIN accounts a ON a.id = e.account_id
                    WHERE e.transfer_id = ?::uuid
                    ORDER BY e.id""", received.transferId());
            Assertions.assertThat(entries)
                    .extracting(row -> row.text("kind"), row -> row.text("direction"),
                            row -> row.text("amount"), row -> row.text("currency"))
                    .containsExactly(
                            tuple("CUSTOMER", "DEBIT", "100.00", "GEL"),
                            tuple("FX_POSITION", "CREDIT", "100.00", "GEL"),
                            tuple("FX_POSITION", "DEBIT", "37.00", "USD"),
                            tuple("CUSTOMER", "CREDIT", "37.00", "USD"));
            Row audit = db.one("""
                    SELECT event_type, actor_id::text AS actor
                    FROM audit_events
                    WHERE entity_type = 'TRANSFER' AND entity_id = ?::uuid""", received.transferId());
            Assertions.assertThat(audit.text("event_type")).isEqualTo("TRANSFER_COMPLETED");
            Assertions.assertThat(audit.text("actor")).isEqualTo(viaApi.id());
        });
    }

    @Test
    @Requirement({"TRF-01", "UI-01", "UI-02", "STM-01"})
    void a_payment_sent_by_one_customer_is_seen_by_the_other_after_signing_in(Page page) {
        CustomerSession sender = Customers.registered();
        Account sendersAccount = sender.accountWithBalance(Currency.GEL, "200.00");
        CustomerSession recipient = Customers.registered();
        Account recipientsAccount = recipient.openAccount(Currency.GEL);

        DashboardPage sendersView = DashboardPage.openAs(page, sender);
        sendersView.transferForm().fill(sendersAccount, recipientsAccount.number(), "75.50").describe("Dinner").send();
        assertThat(sendersView.notice()).hasText("Transfer completed. 75.50 GEL sent.");

        DashboardPage recipientsView = sendersView.signOut().signIn(recipient.email(), recipient.password());

        assertThat(recipientsView.account(recipientsAccount).balance()).hasText("75.50");
        assertThat(recipientsView.statement().descriptions()).hasText(new String[] {"Dinner"});
        assertThat(recipientsView.statement().paidIn()).hasText(new String[] {"75.50"});
        assertThat(recipientsView.accountRows()).hasCount(1);
    }
}
