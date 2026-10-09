package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Business rules of a transfer, tested at their boundaries.
 *
 * <p>Each limit gets three values: just inside, exactly on and just outside. The last group
 * walks the decision table that says which rule is reported when several are broken at once.
 */
@ApiTest
@Epic("Money movement")
@Feature("Transfer rules")
class TransferRulesTest {

    private CustomerSession customer;

    @BeforeEach
    void registerCustomer() {
        customer = Customers.registered();
    }

    @Test
    @Requirement("TRF-03")
    void an_account_cannot_pay_itself() {
        Account account = customer.accountWithBalance(Currency.GEL, "100.00");

        customer.sendTransfer(account, account, "10.00")
                .then().statusCode(422)
                .body("code", equalTo("SAME_ACCOUNT"))
                .body("transferId", notNullValue());

        assertThat(customer.balanceOf(account)).isEqualByComparingTo("100.00");
    }

    @Nested
    @DisplayName("The limit of 10000.00 per transfer")
    @Requirement("TRF-05")
    class PerTransferLimit {

        @ParameterizedTest(name = "{0} is within the limit")
        @ValueSource(strings = {"9999.99", "10000.00"})
        void amounts_up_to_the_limit_are_sent(String amount) {
            Account source = customer.accountWithBalance(Currency.GEL, "20000.00");
            Account target = customer.openAccount(Currency.GEL);

            customer.sendTransfer(source, target, amount)
                    .then().statusCode(201);

            assertThat(customer.balanceOf(target)).isEqualByComparingTo(amount);
        }

        @Test
        void one_cent_over_the_limit_is_rejected() {
            Account source = customer.accountWithBalance(Currency.GEL, "20000.00");
            Account target = customer.openAccount(Currency.GEL);

            customer.sendTransfer(source, target, "10000.01")
                    .then().statusCode(422)
                    .body("code", equalTo("LIMIT_EXCEEDED"));

            assertThat(customer.balanceOf(source)).isEqualByComparingTo("20000.00");
        }

        @Test
        void the_limit_counts_the_amount_in_the_source_currency() {
            Account dollars = customer.accountWithBalance(Currency.USD, "20000.00");
            Account lari = customer.openAccount(Currency.GEL);

            customer.sendTransfer(dollars, lari, "10000.00")
                    .then().statusCode(201)
                    .body("targetAmount", equalTo("26500.00"));
            customer.sendTransfer(dollars, lari, "10000.01")
                    .then().statusCode(422)
                    .body("code", equalTo("LIMIT_EXCEEDED"));
        }
    }

    @Nested
    @DisplayName("Available funds")
    @Requirement("TRF-06")
    class AvailableFunds {

        private Account source;
        private Account target;

        @BeforeEach
        void accountHoldingFifty() {
            source = customer.accountWithBalance(Currency.GEL, "50.00");
            target = customer.openAccount(Currency.GEL);
        }

        @ParameterizedTest(name = "{0} out of 50.00 is sent")
        @ValueSource(strings = {"49.99", "50.00"})
        void amounts_up_to_the_balance_are_sent(String amount) {
            customer.sendTransfer(source, target, amount)
                    .then().statusCode(201);

            assertThat(customer.balanceOf(source).add(customer.balanceOf(target))).isEqualByComparingTo("50.00");
        }

        @Test
        void one_cent_more_than_the_balance_is_rejected() {
            customer.sendTransfer(source, target, "50.01")
                    .then().statusCode(422)
                    .body("code", equalTo("INSUFFICIENT_FUNDS"));

            assertThat(customer.balanceOf(source)).isEqualByComparingTo("50.00");
            assertThat(customer.balanceOf(target)).isEqualByComparingTo("0.00");
        }

        @Test
        void an_empty_account_cannot_send_one_cent() {
            customer.sendTransfer(target, source, "0.01")
                    .then().statusCode(422)
                    .body("code", equalTo("INSUFFICIENT_FUNDS"));
        }
    }

    @Nested
    @DisplayName("When several rules are broken, the first in order is reported")
    @Requirement("TRF-07")
    class RulePrecedence {

        @Test
        void same_account_wins_over_a_frozen_account_and_the_limit() {
            Account account = customer.accountWithBalance(Currency.GEL, "100.00");
            customer.freeze(account);

            customer.sendTransfer(account, account, "10000.01")
                    .then().statusCode(422)
                    .body("code", equalTo("SAME_ACCOUNT"));
        }

        @Test
        void a_frozen_account_wins_over_the_limit_and_missing_funds() {
            Account source = customer.accountWithBalance(Currency.GEL, "100.00");
            Account target = customer.openAccount(Currency.GEL);
            customer.freeze(source);

            customer.sendTransfer(source, target, "10000.01")
                    .then().statusCode(422)
                    .body("code", equalTo("ACCOUNT_NOT_ACTIVE"));
        }

        @Test
        void the_limit_wins_over_missing_funds() {
            Account source = customer.accountWithBalance(Currency.GEL, "100.00");
            Account target = customer.openAccount(Currency.GEL);

            customer.sendTransfer(source, target, "10000.01")
                    .then().statusCode(422)
                    .body("code", equalTo("LIMIT_EXCEEDED"));
        }

        @Test
        void an_amount_too_small_to_convert_wins_over_missing_funds() {
            Account emptyLari = customer.openAccount(Currency.GEL);
            Account dollars = customer.openAccount(Currency.USD);

            customer.sendTransfer(emptyLari, dollars, "0.01")
                    .then().statusCode(422)
                    .body("code", equalTo("AMOUNT_TOO_SMALL"));
        }
    }
}
