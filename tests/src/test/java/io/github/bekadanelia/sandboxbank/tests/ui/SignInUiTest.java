package io.github.bekadanelia.sandboxbank.tests.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.config.TestConfig;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.Smoke;
import io.github.bekadanelia.sandboxbank.junit.UiTest;
import io.github.bekadanelia.sandboxbank.support.Jwt;
import io.github.bekadanelia.sandboxbank.ui.pages.DashboardPage;
import io.github.bekadanelia.sandboxbank.ui.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@UiTest
@Epic("Web client")
@Feature("Sign in")
@Requirement("UI-01")
class SignInUiTest {

    private static CustomerSession customer;

    @BeforeAll
    static void registerCustomer() {
        customer = Customers.registered();
    }

    @Test
    @Smoke
    void a_customer_signs_in_and_lands_on_their_accounts(Page page) {
        DashboardPage dashboard = LoginPage.open(page).signIn(customer.email(), customer.password());

        assertThat(dashboard.customerName()).hasText(customer.customer().fullName());
        assertThat(page).hasTitle("Your accounts | Sandbox Bank");
        assertThat(dashboard.heading()).isFocused();
    }

    @Test
    void a_visitor_who_is_not_signed_in_is_not_offered_to_sign_out(Page page) {
        LoginPage login = LoginPage.open(page);

        assertThat(login.signOutButton()).isHidden();
    }

    @Test
    void a_wrong_password_is_reported_and_the_customer_stays_on_the_sign_in_page(Page page) {
        LoginPage login = LoginPage.open(page).submit(customer.email(), "not the password");

        assertThat(login.alert()).hasText("Email or password is incorrect.");
        assertThat(login.heading()).isVisible();
    }

    @Test
    void empty_fields_are_explained_at_each_field(Page page) {
        LoginPage login = LoginPage.open(page);

        login.signInButton().click();

        assertThat(login.email()).hasAccessibleDescription("Enter your email");
        assertThat(login.password()).hasAccessibleDescription("Enter your password");
        assertThat(login.email()).hasAttribute("aria-invalid", "true");
        assertThat(login.email()).isFocused();
    }

    @Test
    @Requirement("UI-06")
    void sign_in_works_with_the_keyboard_alone(Page page) {
        LoginPage.open(page);

        page.keyboard().press("Tab");
        page.keyboard().type(customer.email());
        page.keyboard().press("Tab");
        page.keyboard().type(customer.password());
        page.keyboard().press("Enter");

        assertThat(DashboardPage.expectOn(page).customerName()).hasText(customer.customer().fullName());
    }

    @Test
    void the_session_survives_a_reload(Page page) {
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        page.reload();

        assertThat(dashboard.customerName()).hasText(customer.customer().fullName());
    }

    @Test
    void signing_out_ends_the_session_for_good(Page page) {
        LoginPage login = DashboardPage.openAs(page, customer).signOut();

        assertThat(login.notice()).hasText("You have signed out.");
        assertThat(login.signOutButton()).isHidden();
        page.navigate("/#/accounts");
        page.reload();
        assertThat(login.heading()).isVisible();
    }

    @Test
    void without_a_session_the_dashboard_address_shows_the_sign_in_page(Page page) {
        page.navigate("/#/accounts");

        assertThat(LoginPage.expectOn(page).heading()).isVisible();
    }

    @Test
    void an_expired_session_sends_the_customer_back_to_sign_in_with_an_explanation(Page page) {
        DashboardPage dashboard = DashboardPage.openAs(page, customer);
        Instant now = Instant.now();
        String expired = Jwt.signed(customer.id(), TestConfig.jwtSecret(),
                now.minus(31, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES));
        page.evaluate("token => { sessionStorage.setItem('sandbox-bank.token', token); }", expired);

        // After the reload the page starts with the expired token and its first request is refused.
        page.reload();

        LoginPage login = LoginPage.expectOn(page);
        assertThat(login.alert()).hasText("Your session has expired. Sign in again.");
        assertThat(dashboard.heading()).isHidden();
    }

    @Test
    void the_dashboard_of_one_customer_never_shows_another_customers_accounts(Page page) {
        CustomerSession other = Customers.registered();
        other.openAccount(Currency.GEL);

        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        assertThat(dashboard.emptyAccountsMessage()).isVisible();
        assertThat(dashboard.accountRows()).hasCount(0);
    }
}
