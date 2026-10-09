package io.github.bekadanelia.sandboxbank.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.AriaRole;
import io.qameta.allure.Step;
import java.util.regex.Pattern;

/** One line of the accounts table. */
public final class AccountRow {

    private final Locator row;

    AccountRow(Locator row) {
        this.row = row;
    }

    public Locator row() {
        return row;
    }

    public Locator balance() {
        return row.getByTestId("balance");
    }

    public Locator status() {
        return row.getByTestId("status");
    }

    @Step("Show the statement of the account")
    public void showStatement() {
        button("Statement of").click();
    }

    @Step("Freeze the account")
    public void freeze() {
        button("Freeze").click();
    }

    @Step("Unfreeze the account")
    public void unfreeze() {
        button("Unfreeze").click();
    }

    /** Buttons carry the account number in their accessible name, for example "Freeze GE42ZZ...". */
    private Locator button(String nameStart) {
        return row.getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(Pattern.compile("^" + nameStart + " ")));
    }
}
