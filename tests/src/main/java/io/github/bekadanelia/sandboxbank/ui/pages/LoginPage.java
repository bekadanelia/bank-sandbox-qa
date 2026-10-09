package io.github.bekadanelia.sandboxbank.ui.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static io.github.bekadanelia.sandboxbank.ui.pages.Names.named;
import static io.github.bekadanelia.sandboxbank.ui.pages.Names.pageNamed;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;

/**
 * The sign in view.
 *
 * <p>Page objects in this suite find elements the way a user or a screen reader does, by role
 * and label. A locator that stops working then usually means the page lost its accessibility,
 * not that a class name changed.
 */
public final class LoginPage {

    private final Page page;
    private final Locator view;

    LoginPage(Page page) {
        this.page = page;
        this.view = page.getByRole(AriaRole.REGION, pageNamed("Sign in"));
    }

    @Step("Open the sign in page")
    public static LoginPage open(Page page) {
        page.navigate("/");
        return expectOn(page);
    }

    /** Waits until the sign in view is the one on screen. */
    public static LoginPage expectOn(Page page) {
        LoginPage login = new LoginPage(page);
        assertThat(login.heading()).isVisible();
        return login;
    }

    @Step("Sign in as {email}")
    public DashboardPage signIn(String email, String password) {
        submit(email, password);
        return DashboardPage.expectOn(page);
    }

    @Step("Submit the sign in form")
    public LoginPage submit(String email, String password) {
        email().fill(email);
        password().fill(password);
        signInButton().click();
        return this;
    }

    @Step("Go to registration")
    public RegistrationPage createAccount() {
        view.getByRole(AriaRole.LINK, named("Create an account")).click();
        return RegistrationPage.expectOn(page);
    }

    public Locator heading() {
        return view.getByRole(AriaRole.HEADING, named("Sign in"));
    }

    public Locator email() {
        return view.getByLabel("Email");
    }

    public Locator password() {
        return view.getByLabel("Password");
    }

    public Locator signInButton() {
        return view.getByRole(AriaRole.BUTTON, named("Sign in"));
    }

    /** Belongs to the page header and must stay hidden until a customer has signed in. */
    public Locator signOutButton() {
        return page.getByRole(AriaRole.BUTTON, pageNamed("Sign out"));
    }

    public Locator alert() {
        return page.getByRole(AriaRole.ALERT);
    }

    public Locator notice() {
        return page.getByRole(AriaRole.STATUS);
    }
}
