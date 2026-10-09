package io.github.bekadanelia.sandboxbank.tests.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.TransferRequest;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.UiTest;
import io.github.bekadanelia.sandboxbank.ui.pages.DashboardPage;
import io.github.bekadanelia.sandboxbank.ui.pages.StatementPanel;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UiTest
@Epic("Web client")
@Feature("Statement")
@Requirement("STM-01")
class StatementUiTest {

    private CustomerSession customer;
    private Account account;

    @BeforeEach
    void customerWithAnAccount() {
        customer = Customers.registered();
        account = customer.openAccount(Currency.GEL);
    }

    @Test
    void entries_are_shown_newest_first_with_money_out_and_money_in_in_their_own_columns(Page page) {
        Account other = customer.openAccount(Currency.GEL);
        customer.deposit(account, "100.00");
        customer.api().transfer(TestData.idempotencyKey(),
                TransferRequest.of(account, other, "30.00").describedAs("Electricity")).then().statusCode(201);

        StatementPanel statement = DashboardPage.openAs(page, customer).statement();

        assertThat(statement.account()).hasText(account.number() + " (GEL)");
        assertThat(statement.rows()).hasCount(2);
        assertThat(statement.descriptions()).hasText(new String[] {"Electricity", ""});
        assertThat(statement.paidOut()).hasText(new String[] {"30.00", ""});
        assertThat(statement.paidIn()).hasText(new String[] {"", "100.00"});
        assertThat(statement.balances()).hasText(new String[] {"70.00", "100.00"});
    }

    @Test
    @Requirement("STM-02")
    void a_long_statement_is_paged_ten_entries_at_a_time(Page page) {
        for (int deposit = 1; deposit <= 12; deposit++) {
            customer.deposit(account, deposit + ".00");
        }
        StatementPanel statement = DashboardPage.openAs(page, customer).statement();

        assertThat(statement.pageIndicator()).hasText("Page 1 of 2");
        assertThat(statement.rows()).hasCount(10);
        assertThat(statement.paidIn().first()).hasText("12.00");
        assertThat(statement.previousButton()).isDisabled();

        statement.next();

        assertThat(statement.pageIndicator()).hasText("Page 2 of 2");
        assertThat(statement.paidIn()).hasText(new String[] {"2.00", "1.00"});
        assertThat(statement.nextButton()).isDisabled();

        statement.previous();

        assertThat(statement.pageIndicator()).hasText("Page 1 of 2");
        assertThat(statement.rows()).hasCount(10);
    }

    @Test
    void choosing_another_account_shows_that_accounts_statement(Page page) {
        Account dollars = customer.accountWithBalance(Currency.USD, "55.00");
        customer.deposit(account, "9.00");
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        dashboard.account(dollars).showStatement();

        assertThat(dashboard.statement().account()).hasText(dollars.number() + " (USD)");
        assertThat(dashboard.statement().paidIn()).hasText(new String[] {"55.00"});
    }

    @Test
    void an_account_without_entries_says_so(Page page) {
        StatementPanel statement = DashboardPage.openAs(page, customer).statement();

        assertThat(statement.emptyMessage()).isVisible();
        assertThat(statement.rows()).hasCount(0);
    }
}
