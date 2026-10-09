package io.github.bekadanelia.sandboxbank.ui.pages;

import static io.github.bekadanelia.sandboxbank.ui.pages.Names.named;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;

/** The statement of the selected account, ten entries per page, newest first. */
public final class StatementPanel {

    private final Locator panel;

    StatementPanel(Locator panel) {
        this.panel = panel;
    }

    /** Number and currency of the account the statement belongs to. */
    public Locator account() {
        return panel.getByTestId("statement-account");
    }

    public Locator rows() {
        return panel.locator("tbody tr");
    }

    public Locator descriptions() {
        return panel.getByTestId("description");
    }

    public Locator paidOut() {
        return panel.getByTestId("paid-out");
    }

    public Locator paidIn() {
        return panel.getByTestId("paid-in");
    }

    public Locator balances() {
        return panel.getByTestId("balance-after");
    }

    public Locator emptyMessage() {
        return panel.getByText("No entries yet.");
    }

    public Locator pageIndicator() {
        return panel.getByTestId("statement-page");
    }

    public Locator nextButton() {
        return panel.getByRole(AriaRole.BUTTON, named("Next"));
    }

    public Locator previousButton() {
        return panel.getByRole(AriaRole.BUTTON, named("Previous"));
    }

    @Step("Go to the next statement page")
    public void next() {
        nextButton().click();
    }

    @Step("Go to the previous statement page")
    public void previous() {
        previousButton().click();
    }
}
