package io.github.bekadanelia.sandboxbank.ui.pages;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static io.github.bekadanelia.sandboxbank.ui.pages.Names.named;
import static io.github.bekadanelia.sandboxbank.ui.pages.Names.pageNamed;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import io.github.bekadanelia.sandboxbank.api.model.RegistrationRequest;
import io.qameta.allure.Step;

/** The view where a visitor becomes a customer. */
public final class RegistrationPage {

    private final Page page;
    private final Locator view;

    RegistrationPage(Page page) {
        this.page = page;
        this.view = page.getByRole(AriaRole.REGION, pageNamed("Create an account"));
    }

    static RegistrationPage expectOn(Page page) {
        RegistrationPage registration = new RegistrationPage(page);
        assertThat(registration.heading()).isVisible();
        return registration;
    }

    @Step("Register through the form")
    public DashboardPage register(RegistrationRequest registration) {
        submit(registration);
        return DashboardPage.expectOn(page);
    }

    @Step("Submit the registration form")
    public RegistrationPage submit(RegistrationRequest registration) {
        fullName().fill(registration.fullName());
        email().fill(registration.email());
        password().fill(registration.password());
        view.getByRole(AriaRole.BUTTON, named("Create account")).click();
        return this;
    }

    public Locator heading() {
        return view.getByRole(AriaRole.HEADING, named("Create an account"));
    }

    public Locator fullName() {
        return view.getByLabel("Full name");
    }

    public Locator email() {
        return view.getByLabel("Email");
    }

    public Locator password() {
        return view.getByLabel("Password");
    }
}
