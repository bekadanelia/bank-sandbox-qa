package io.github.bekadanelia.sandboxbank.tests.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.deque.html.axecore.results.Rule;
import com.microsoft.playwright.Page;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.RegistrationRequest;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.UiTest;
import io.github.bekadanelia.sandboxbank.ui.pages.DashboardPage;
import io.github.bekadanelia.sandboxbank.ui.pages.LoginPage;
import io.github.bekadanelia.sandboxbank.ui.pages.RegistrationPage;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Automated accessibility checks with axe against WCAG 2.1 levels A and AA.
 *
 * <p>Each view is scanned in the state where it shows the most: with data, with field errors
 * and with a message on screen. A scan finds only a part of all accessibility problems. It does
 * not replace a session with a screen reader, it makes sure the basics never regress.
 */
@UiTest
@Epic("Web client")
@Feature("Accessibility")
@Requirement("UI-06")
class AccessibilityUiTest {

    private static final List<String> WCAG_21_A_AND_AA = List.of("wcag2a", "wcag2aa", "wcag21a", "wcag21aa");

    private static List<Rule> scan(Page page) {
        return new AxeBuilder(page).withTags(WCAG_21_A_AND_AA).analyze().getViolations();
    }

    private static void assertNoViolations(Page page) {
        List<Rule> violations = scan(page);
        String report = violations.stream()
                .map(rule -> "%s (%s): %s%n%s%n".formatted(rule.getId(), rule.getImpact(), rule.getHelp(),
                        rule.getNodes().stream()
                                .map(node -> "  " + node.getHtml())
                                .collect(Collectors.joining("\n"))))
                .collect(Collectors.joining("\n"));
        if (!violations.isEmpty()) {
            Allure.attachment("Accessibility violations", report);
        }
        Assertions.assertThat(violations).as("WCAG 2.1 A and AA violations:%n%s", report).isEmpty();
    }

    /** Guards the scans below. A scanner that finds nothing on a broken page proves nothing on a good one. */
    @Test
    void the_scan_finds_a_defect_that_is_planted_on_purpose(Page page) {
        LoginPage.open(page);
        page.evaluate("() => document.querySelector('label[for=\"login-email\"]').remove()");

        Assertions.assertThat(scan(page)).extracting(Rule::getId).contains("label");
    }

    @Test
    void the_sign_in_page_passes_the_scan(Page page) {
        LoginPage.open(page);

        assertNoViolations(page);
    }

    @Test
    void the_sign_in_page_showing_field_errors_and_an_alert_passes_the_scan(Page page) {
        LoginPage login = LoginPage.open(page);
        login.signInButton().click();
        assertThat(login.email()).hasAttribute("aria-invalid", "true");

        assertNoViolations(page);

        login.submit("nobody@example.test", "not the password");
        assertThat(login.alert()).isVisible();

        assertNoViolations(page);
    }

    @Test
    void the_registration_page_showing_field_errors_passes_the_scan(Page page) {
        RegistrationPage registration = LoginPage.open(page).createAccount();
        registration.submit(new RegistrationRequest("not an email", "N", "short"));
        assertThat(registration.email()).hasAttribute("aria-invalid", "true");

        assertNoViolations(page);
    }

    @Test
    void the_dashboard_with_accounts_a_statement_and_a_rejection_passes_the_scan(Page page) {
        CustomerSession customer = Customers.registered();
        Account lari = customer.accountWithBalance(Currency.GEL, "1250.00");
        Account dollars = customer.openAccount(Currency.USD);
        customer.transfer(lari, dollars, "100.00");
        customer.freeze(dollars);
        DashboardPage dashboard = DashboardPage.openAs(page, customer);
        dashboard.transferForm().fill(lari, dollars.number(), "5.00").send();
        assertThat(dashboard.alert()).isVisible();

        assertNoViolations(page);
    }

    /** WCAG 1.4.10 Reflow: at a width of 320 CSS pixels the page must not scroll sideways. */
    @Test
    void the_dashboard_fits_a_narrow_phone_without_scrolling_sideways(Page page) {
        CustomerSession customer = Customers.registered();
        customer.accountWithBalance(Currency.GEL, "1250.00");
        page.setViewportSize(320, 720);

        DashboardPage.openAs(page, customer);

        int overflow = (int) page.evaluate(
                "() => document.documentElement.scrollWidth - document.documentElement.clientWidth");
        Assertions.assertThat(overflow).as("pixels the page is wider than the screen").isZero();
    }

    /**
     * The page objects find both messages by ARIA role, so this passes only while a confirmation
     * is a polite status and a refusal is an alert that screen readers announce at once.
     */
    @Test
    void confirmations_and_refusals_are_announced_through_separate_live_regions(Page page) {
        CustomerSession customer = Customers.registered();
        Account lari = customer.openAccount(Currency.GEL);
        DashboardPage dashboard = DashboardPage.openAs(page, customer);

        dashboard.addFundsForm().add(lari, "10.00");
        assertThat(dashboard.notice()).hasText("Funds added. 10.00 GEL received.");
        assertThat(dashboard.alert()).isHidden();

        dashboard.transferForm().fill(lari, lari.number(), "1.00").send();
        assertThat(dashboard.alert()).hasText("Transfer rejected. Source and target accounts must be different.");
        assertThat(dashboard.notice()).isHidden();
    }
}
