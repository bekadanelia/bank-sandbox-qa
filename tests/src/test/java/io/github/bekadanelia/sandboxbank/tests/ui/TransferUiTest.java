package io.github.bekadanelia.sandboxbank.tests.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.Smoke;
import io.github.bekadanelia.sandboxbank.junit.UiTest;
import io.github.bekadanelia.sandboxbank.support.Iban;
import io.github.bekadanelia.sandboxbank.ui.pages.DashboardPage;
import io.github.bekadanelia.sandboxbank.ui.pages.TransferForm;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UiTest
@Epic("Web client")
@Feature("Transfers")
class TransferUiTest {

    private static final String TRANSFERS_ENDPOINT = "**/api/v1/transfers";

    private CustomerSession customer;
    private Account source;
    private Account target;

    @BeforeEach
    void customerWithTwoAccounts() {
        customer = Customers.registered();
        source = customer.accountWithBalance(Currency.GEL, "250.00");
        target = customer.openAccount(Currency.GEL);
    }

    @Test
    @Smoke
    @Requirement("UI-02")
    void a_transfer_updates_both_balances_and_the_statement_without_a_reload(Page page) {
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        dashboard.transferForm().fill(source, target.number(), "100.00").describe("Savings").send();

        assertThat(dashboard.notice()).hasText("Transfer completed. 100.00 GEL sent.");
        assertThat(dashboard.account(source).balance()).hasText("150.00");
        assertThat(dashboard.account(target).balance()).hasText("100.00");
        assertThat(dashboard.statement().descriptions().first()).hasText("Savings");
        assertThat(dashboard.statement().paidOut().first()).hasText("100.00");
        assertThat(dashboard.statement().balances().first()).hasText("150.00");
    }

    @Test
    @Requirement({"UI-02", "FX-01"})
    void a_conversion_shows_what_was_sent_what_arrived_and_at_which_rate(Page page) {
        Account dollars = customer.openAccount(Currency.USD);
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        dashboard.transferForm().fill(source, dollars.number(), "10.02").send();

        assertThat(dashboard.notice())
                .hasText("Transfer completed. 10.02 GEL sent, 3.71 USD received at rate 0.370000.");
        assertThat(dashboard.account(dollars).balance()).hasText("3.71");
    }

    @Test
    @Requirement({"UI-03", "TRF-06"})
    void a_rejected_transfer_explains_why_and_leaves_the_balances_alone(Page page) {
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        dashboard.transferForm().fill(source, target.number(), "250.01").send();

        assertThat(dashboard.alert())
                .hasText("Transfer rejected. The source account does not have enough funds.");
        assertThat(dashboard.account(source).balance()).hasText("250.00");
        assertThat(dashboard.account(target).balance()).hasText("0.00");
    }

    @Test
    @Requirement("UI-03")
    void an_invalid_amount_is_stopped_in_the_browser_before_any_request_is_sent(Page page) {
        List<Request> transferRequests = recordTransferRequests(page);
        DashboardPage dashboard = DashboardPage.openAs(page, customer);
        TransferForm form = dashboard.transferForm();

        form.fill(source, target.number(), "12.345").send();

        assertThat(form.amount()).hasAccessibleDescription("Enter an amount such as 25.00");
        assertThat(form.amount()).isFocused();
        Assertions.assertThat(transferRequests).isEmpty();
    }

    @Test
    @Requirement({"UI-03", "TRF-09"})
    void a_mistyped_account_number_is_explained_at_its_field(Page page) {
        DashboardPage dashboard = DashboardPage.openAs(page, customer);
        TransferForm form = dashboard.transferForm();

        form.fill(source, Iban.withWrongCheckDigits(target.number()), "10.00").send();

        assertThat(form.targetAccountNumber()).hasAccessibleDescription("Account number has invalid check digits");
        assertThat(dashboard.account(source).balance()).hasText("250.00");
    }

    @Test
    @Requirement("UI-04")
    void a_double_click_on_send_makes_one_transfer(Page page) {
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        dashboard.transferForm().fill(source, target.number(), "40.00").sendButton().dblclick();

        assertThat(dashboard.notice()).hasText("Transfer completed. 40.00 GEL sent.");
        assertThat(dashboard.account(source).balance()).hasText("210.00");
        Assertions.assertThat(customer.balanceOf(target)).isEqualByComparingTo("40.00");
    }

    /**
     * The worst moment for a payment to fail: the bank has moved the money but the answer never
     * reaches the browser. The customer sees an error and presses Send again. The test cuts the
     * first answer off on the wire to create exactly that situation.
     */
    @Test
    @Requirement({"UI-04", "IDM-02"})
    void sending_again_after_a_lost_answer_does_not_pay_twice(Page page) {
        List<Request> transferRequests = recordTransferRequests(page);
        page.route(TRANSFERS_ENDPOINT, route -> {
            route.fetch();     // the bank executes the transfer
            route.abort();     // the browser never hears about it
        }, new Page.RouteOptions().setTimes(1));
        DashboardPage dashboard = DashboardPage.openAs(page, customer);
        TransferForm form = dashboard.transferForm();

        form.fill(source, target.number(), "60.00").send();
        assertThat(dashboard.alert()).containsText("The bank did not confirm the result.");
        form.send();

        assertThat(dashboard.notice()).hasText("Transfer completed. 60.00 GEL sent.");
        assertThat(dashboard.account(source).balance()).hasText("190.00");   // 250.00 minus 60.00, once
        Assertions.assertThat(transferRequests).hasSize(2);
        Assertions.assertThat(transferRequests.get(1).headerValue("idempotency-key"))
                .as("the second attempt reuses the key of the first")
                .isEqualTo(transferRequests.get(0).headerValue("idempotency-key"));
        Assertions.assertThat(customer.balanceOf(target)).isEqualByComparingTo("60.00");
    }

    @Test
    @Requirement("UI-03")
    void a_server_error_is_reported_and_the_form_keeps_what_was_typed(Page page) {
        page.route(TRANSFERS_ENDPOINT, route -> route.fulfill(new Route.FulfillOptions()
                .setStatus(500)
                .setContentType("application/json")
                .setBody("{\"code\":\"INTERNAL_ERROR\",\"message\":\"Unexpected error\",\"requestId\":\"mocked\"}")));
        DashboardPage dashboard = DashboardPage.openAs(page, customer);
        TransferForm form = dashboard.transferForm();

        form.fill(source, target.number(), "75.00").send();

        assertThat(dashboard.alert()).containsText("The bank did not confirm the result.");
        assertThat(form.amount()).hasValue("75.00");
        assertThat(form.targetAccountNumber()).hasValue(target.number());
        assertThat(form.sendButton()).isEnabled();
    }

    @Test
    @Requirement("UI-05")
    void text_typed_by_a_customer_is_shown_as_text_and_never_run_as_html(Page page) {
        String payload = "<img src=x onerror=\"document.title='injected'\">";
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        dashboard.transferForm().fill(source, target.number(), "1.00").describe(payload).send();

        assertThat(dashboard.statement().descriptions().first()).hasText(payload);
        assertThat(dashboard.statement().rows().locator("img")).hasCount(0);
        assertThat(page).hasTitle("Your accounts | Sandbox Bank");
    }

    private static List<Request> recordTransferRequests(Page page) {
        List<Request> requests = new CopyOnWriteArrayList<>();
        page.onRequest(request -> {
            if ("POST".equals(request.method()) && request.url().endsWith("/api/v1/transfers")) {
                requests.add(request);
            }
        });
        return requests;
    }
}
