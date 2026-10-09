package io.github.bekadanelia.sandboxbank.tests.framework;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bekadanelia.sandboxbank.support.Iban;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The check digit helper is the oracle for account number tests, so it is verified against a published example. */
@Tag("framework")
@Epic("Test framework")
@Feature("Check digit oracle")
class IbanTest {

    /** The example IBAN for Georgia in the ISO 13616 registry. */
    private static final String REGISTRY_EXAMPLE = "GE29NB0000000101904917";

    @Test
    void the_registry_example_is_valid() {
        assertThat(Iban.hasValidCheckDigits(REGISTRY_EXAMPLE)).isTrue();
    }

    @Test
    void a_single_changed_digit_is_detected() {
        assertThat(Iban.hasValidCheckDigits("GE29NB0000000101904918")).isFalse();
    }

    @Test
    void two_swapped_digits_are_detected() {
        assertThat(Iban.hasValidCheckDigits("GE29NB0000000101909417")).isFalse();
    }

    @Test
    void replacing_the_check_digits_makes_a_valid_number_invalid() {
        assertThat(Iban.hasValidCheckDigits(Iban.withWrongCheckDigits(REGISTRY_EXAMPLE))).isFalse();
    }

    @Test
    void the_number_used_for_unknown_accounts_is_itself_well_formed() {
        assertThat(Iban.unissued()).matches("GE\\d{2}ZZ\\d{16}");
        assertThat(Iban.hasValidCheckDigits(Iban.unissued())).isTrue();
    }
}
