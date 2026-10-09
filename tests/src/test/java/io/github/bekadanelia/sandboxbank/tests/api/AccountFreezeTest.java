package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.DepositRequest;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ApiTest
@Epic("Accounts")
@Feature("Freezing accounts")
@Requirement("ACC-05")
class AccountFreezeTest {

    private CustomerSession customer;
    private Account funded;
    private Account other;

    @BeforeEach
    void customerWithTwoAccounts() {
        customer = Customers.registered();
        funded = customer.accountWithBalance(Currency.GEL, "100.00");
        other = customer.accountWithBalance(Currency.GEL, "100.00");
    }

    @Test
    void freezing_changes_the_status_and_keeps_the_balance() {
        customer.api().freeze(funded.id())
                .then().statusCode(200)
                .body("status", equalTo("FROZEN"))
                .body("balance", equalTo("100.00"));

        assertThat(customer.account(funded.id()).status()).isEqualTo(Account.Status.FROZEN);
    }

    @Test
    void freezing_a_frozen_account_succeeds_and_changes_nothing() {
        customer.freeze(funded);

        customer.api().freeze(funded.id())
                .then().statusCode(200)
                .body("status", equalTo("FROZEN"));
    }

    @Test
    @Requirement("TRF-04")
    void a_frozen_account_cannot_send_money() {
        customer.freeze(funded);

        customer.sendTransfer(funded, other, "10.00")
                .then().statusCode(422)
                .body("code", equalTo("ACCOUNT_NOT_ACTIVE"));

        assertThat(customer.balanceOf(funded)).isEqualByComparingTo("100.00");
        assertThat(customer.balanceOf(other)).isEqualByComparingTo("100.00");
    }

    @Test
    @Requirement("TRF-04")
    void a_frozen_account_cannot_receive_a_transfer() {
        customer.freeze(other);

        customer.sendTransfer(funded, other, "10.00")
                .then().statusCode(422)
                .body("code", equalTo("ACCOUNT_NOT_ACTIVE"));

        assertThat(customer.balanceOf(funded)).isEqualByComparingTo("100.00");
    }

    @Test
    void a_frozen_account_cannot_receive_a_deposit() {
        customer.freeze(funded);

        customer.api().deposit(funded.id(), TestData.idempotencyKey(), DepositRequest.of("10.00"))
                .then().statusCode(422)
                .body("code", equalTo("ACCOUNT_NOT_ACTIVE"));

        assertThat(customer.balanceOf(funded)).isEqualByComparingTo("100.00");
    }

    @Test
    @Requirement("TRF-04")
    void an_unfrozen_account_moves_money_again() {
        customer.freeze(funded);

        customer.api().unfreeze(funded.id())
                .then().statusCode(200)
                .body("status", equalTo("ACTIVE"));

        customer.transfer(funded, other, "10.00");
        assertThat(customer.balanceOf(funded)).isEqualByComparingTo("90.00");
    }
}
