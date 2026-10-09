package io.github.bekadanelia.sandboxbank.ui.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static io.github.bekadanelia.sandboxbank.ui.pages.Names.named;
import static io.github.bekadanelia.sandboxbank.ui.pages.Names.pageNamed;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.qameta.allure.Step;

/** The view a signed in customer works in: accounts, statement and the money forms. */
public final class DashboardPage {

    private static final String TOKEN_KEY = "sandbox-bank.token";

    private final Page page;
    private final Locator view;

    DashboardPage(Page page) {
        this.page = page;
        this.view = page.getByRole(AriaRole.REGION, pageNamed("Your accounts"));
    }

    /**
     * Opens the dashboard for a customer without going through the sign in form.
     *
     * <p>The token from the API login is placed where the web client keeps it. Only the sign in
     * tests type credentials. Everything else starts here, which keeps UI tests short and
     * means a broken login form fails the login tests instead of all of them.
     */
    @Step("Open the dashboard with the session of a customer")
    public static DashboardPage openAs(Page page, CustomerSession customer) {
        page.navigate("/");
        page.evaluate("([key, token]) => sessionStorage.setItem(key, token)",
                new String[] {TOKEN_KEY, customer.token()});
        page.reload();
        return expectOn(page);
    }

    /** Waits until the dashboard is the view on screen. */
    public static DashboardPage expectOn(Page page) {
        DashboardPage dashboard = new DashboardPage(page);
        assertThat(dashboard.heading()).isVisible();
        return dashboard;
    }

    public Locator heading() {
        return view.getByRole(AriaRole.HEADING, named("Your accounts"));
    }

    public Locator customerName() {
        return page.getByTestId("customer-name");
    }

    /** The green message that confirms an action. Announced politely to screen readers. */
    public Locator notice() {
        return page.getByRole(AriaRole.STATUS);
    }

    /** The red message that reports a refusal or a failure. Announced at once. */
    public Locator alert() {
        return page.getByRole(AriaRole.ALERT);
    }

    public Locator accountRows() {
        return view.getByRole(AriaRole.TABLE, named("Accounts"))
                .locator("tbody tr");
    }

    public Locator emptyAccountsMessage() {
        return view.getByText("You have no accounts yet");
    }

    public AccountRow account(Account account) {
        return new AccountRow(accountRows().filter(new Locator.FilterOptions().setHasText(account.number())));
    }

    public TransferForm transferForm() {
        return new TransferForm(view.getByRole(AriaRole.FORM, named("Transfer")));
    }

    public AddFundsForm addFundsForm() {
        return new AddFundsForm(view.getByRole(AriaRole.FORM, named("Add funds")));
    }

    public StatementPanel statement() {
        return new StatementPanel(
                view.getByRole(AriaRole.REGION, named("Statement")));
    }

    @Step("Open a {currency} account")
    public DashboardPage openAccount(Currency currency) {
        Locator form = view.getByRole(AriaRole.FORM, named("Open account"));
        form.getByLabel("Currency").selectOption(currency.name());
        form.getByRole(AriaRole.BUTTON, named("Open account")).click();
        return this;
    }

    @Step("Sign out")
    public LoginPage signOut() {
        page.getByRole(AriaRole.BUTTON, pageNamed("Sign out")).click();
        return LoginPage.expectOn(page);
    }
}
