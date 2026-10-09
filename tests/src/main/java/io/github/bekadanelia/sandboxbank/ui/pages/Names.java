package io.github.bekadanelia.sandboxbank.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Options for finding an element by its role and its exact accessible name.
 *
 * <p>Exact matching matters: by default "Statement" would also match a region called
 * "Statement entries, newest first".
 */
final class Names {

    private Names() {
    }

    /** For a search inside another locator. */
    static Locator.GetByRoleOptions named(String name) {
        return new Locator.GetByRoleOptions().setName(name).setExact(true);
    }

    /** For a search in the whole page. */
    static Page.GetByRoleOptions pageNamed(String name) {
        return new Page.GetByRoleOptions().setName(name).setExact(true);
    }
}
