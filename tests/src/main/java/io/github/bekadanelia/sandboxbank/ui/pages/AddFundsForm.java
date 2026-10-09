package io.github.bekadanelia.sandboxbank.ui.pages;

import static io.github.bekadanelia.sandboxbank.ui.pages.Names.named;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.qameta.allure.Step;

/** The form that pays cash into one of the customer's accounts. */
public final class AddFundsForm {

    private final Locator form;

    AddFundsForm(Locator form) {
        this.form = form;
    }

    @Step("Add {amount} to the account")
    public void add(Account account, String amount) {
        form.getByLabel("To account").selectOption(account.id());
        amount().fill(amount);
        form.getByRole(AriaRole.BUTTON, named("Add funds")).click();
    }

    public Locator amount() {
        return form.getByLabel("Amount");
    }
}
