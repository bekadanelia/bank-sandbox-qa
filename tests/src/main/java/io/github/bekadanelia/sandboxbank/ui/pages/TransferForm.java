package io.github.bekadanelia.sandboxbank.ui.pages;

import static io.github.bekadanelia.sandboxbank.ui.pages.Names.named;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.qameta.allure.Step;

/** The form that sends money to another account. */
public final class TransferForm {

    private final Locator form;

    TransferForm(Locator form) {
        this.form = form;
    }

    @Step("Fill a transfer of {amount} to {targetAccountNumber}")
    public TransferForm fill(Account source, String targetAccountNumber, String amount) {
        sourceAccount().selectOption(source.id());
        targetAccountNumber().fill(targetAccountNumber);
        amount().fill(amount);
        return this;
    }

    @Step("Describe the transfer as {text}")
    public TransferForm describe(String text) {
        description().fill(text);
        return this;
    }

    @Step("Press Send")
    public void send() {
        sendButton().click();
    }

    public Locator sourceAccount() {
        return form.getByLabel("From account");
    }

    public Locator targetAccountNumber() {
        return form.getByLabel("To account number");
    }

    public Locator amount() {
        return form.getByLabel("Amount");
    }

    public Locator description() {
        return form.getByLabel("Description (optional)");
    }

    public Locator sendButton() {
        return form.getByRole(AriaRole.BUTTON, named("Send"));
    }
}
