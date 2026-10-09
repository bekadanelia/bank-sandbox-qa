package io.github.bekadanelia.sandboxbank.tests.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.UiTest;
import io.github.bekadanelia.sandboxbank.ui.pages.AccountRow;
import io.github.bekadanelia.sandboxbank.ui.pages.DashboardPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@UiTest
@Epic("Web client")
@Feature("Accounts")
class AccountsUiTest {

    private CustomerSession customer;

    @BeforeEach
    void registerCustomer() {
        customer = Customers.registered();
    }

    @Test
    @Requirement("ACC-01")
    void an_opened_account_appears_in_the_list_with_a_zero_balance(Page page) {
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        dashboard.openAccount(Currency.USD);

        assertThat(dashboard.notice()).hasText(Pattern.compile("^Account GE\\d{2}ZZ\\d{16} \\(USD\\) is open\\.$"));
        assertThat(dashboard.accountRows()).hasCount(1);
        Account opened = customer.accounts().getFirst();
        assertThat(dashboard.account(opened).balance()).hasText("0.00");
        assertThat(dashboard.account(opened).status()).hasText("Active");
    }

    @Test
    @Requirement({"ACC-02", "UI-03"})
    void the_sixth_account_is_refused_with_the_reason(Page page) {
        for (int count = 1; count <= 5; count++) {
            customer.openAccount(Currency.GEL);
        }
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        dashboard.openAccount(Currency.GEL);

        assertThat(dashboard.alert()).hasText("The account was not opened. A customer can hold at most 5 accounts.");
        assertThat(dashboard.accountRows()).hasCount(5);
    }

    @Test
    @Requirement("ACC-05")
    void freezing_and_unfreezing_is_reflected_in_the_status(Page page) {
        Account account = customer.openAccount(Currency.GEL);
        AccountRow row = DashboardPage.openAs(page, customer).account(account);

        row.freeze();
        assertThat(row.status()).hasText("Frozen");

        row.unfreeze();
        assertThat(row.status()).hasText("Active");
    }

    @Test
    @Requirement("DEP-01")
    void added_funds_show_up_in_the_balance_with_thousands_separators(Page page) {
        Account account = customer.openAccount(Currency.GEL);
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        dashboard.addFundsForm().add(account, "12345.6");

        assertThat(dashboard.notice()).hasText("Funds added. 12,345.60 GEL received.");
        assertThat(dashboard.account(account).balance()).hasText("12,345.60");
    }
}
