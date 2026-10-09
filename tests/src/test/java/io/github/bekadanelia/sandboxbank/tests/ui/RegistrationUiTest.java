package io.github.bekadanelia.sandboxbank.tests.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Page;
import io.github.bekadanelia.sandboxbank.api.model.RegistrationRequest;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.UiTest;
import io.github.bekadanelia.sandboxbank.ui.pages.DashboardPage;
import io.github.bekadanelia.sandboxbank.ui.pages.LoginPage;
import io.github.bekadanelia.sandboxbank.ui.pages.RegistrationPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@UiTest
@Epic("Web client")
@Feature("Registration")
class RegistrationUiTest {

    @Test
    @Requirement({"UI-01", "AUTH-01"})
    void a_visitor_registers_and_arrives_on_an_empty_dashboard(Page page) {
        RegistrationRequest registration = new RegistrationRequest(
                TestData.uniqueEmail(), "Nino Beridze", TestData.PASSWORD);

        DashboardPage dashboard = LoginPage.open(page).createAccount().register(registration);

        assertThat(dashboard.customerName()).hasText("Nino Beridze");
        assertThat(dashboard.emptyAccountsMessage()).isVisible();
    }

    @Test
    @Requirement({"UI-03", "AUTH-02"})
    void problems_found_by_the_server_are_shown_at_the_fields_they_belong_to(Page page) {
        RegistrationPage form = LoginPage.open(page).createAccount();

        form.submit(new RegistrationRequest("not an email", "N", "short"));

        assertThat(form.email()).hasAccessibleDescription("Email must be a valid email address");
        assertThat(form.fullName()).hasAccessibleDescription("Full name must be 2 to 100 characters long");
        assertThat(form.password()).hasAccessibleDescription(
                Pattern.compile("Password must be 8 to 72 characters long"));
        assertThat(form.heading()).isVisible();
    }

    @Test
    @Requirement({"UI-03", "AUTH-01"})
    void an_email_that_is_already_registered_is_explained_at_the_email_field(Page page) {
        String takenEmail = Customers.registered().email();
        RegistrationPage form = LoginPage.open(page).createAccount();

        form.submit(new RegistrationRequest(takenEmail, "Nino Beridze", TestData.PASSWORD));

        assertThat(form.email()).hasAccessibleDescription("This email is already registered");
        assertThat(form.email()).isFocused();
    }
}
