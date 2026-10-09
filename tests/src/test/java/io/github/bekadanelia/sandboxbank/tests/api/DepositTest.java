package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.ApiError;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.DepositRequest;
import io.github.bekadanelia.sandboxbank.api.model.Transfer;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.Smoke;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.CsvSource;

@ApiTest
@Epic("Money movement")
@Feature("Deposits")
class DepositTest {

    private CustomerSession customer;
    private Account account;

    @BeforeEach
    void customerWithAnEmptyAccount() {
        customer = Customers.registered();
        account = customer.openAccount(Currency.GEL);
    }

    @Test
    @Smoke
    @Requirement("DEP-01")
    void a_deposit_credits_the_account() {
        Transfer deposit = customer.api()
                .deposit(account.id(), TestData.idempotencyKey(), new DepositRequest("250.00", "Salary"))
                .then().statusCode(201)
                .extract().as(Transfer.class);

        assertThat(deposit.type()).isEqualTo(Transfer.Type.DEPOSIT);
        assertThat(deposit.status()).isEqualTo(Transfer.Status.COMPLETED);
        assertThat(deposit.sourceAccountId()).as("the bank's cash account stays hidden").isNull();
        assertThat(deposit.targetAccountNumber()).isEqualTo(account.number());
        assertThat(deposit.targetAmount()).isEqualByComparingTo("250.00");
        assertThat(deposit.targetCurrency()).isEqualTo(Currency.GEL);
        assertThat(deposit.description()).isEqualTo("Salary");
        assertThat(customer.balanceOf(account)).isEqualByComparingTo("250.00");
    }

    @ParameterizedTest(name = "{0} is stored as {1}")
    @CsvSource({"0.01, 0.01", "7, 7.00", "7.5, 7.50", "100000, 100000.00"})
    @Requirement({"DEP-01", "DEP-02"})
    void amounts_are_stored_with_two_decimals_from_the_smallest_to_the_limit(String sent, String stored) {
        customer.api().deposit(account.id(), TestData.idempotencyKey(), DepositRequest.of(sent))
                .then().statusCode(201)
                .body("targetAmount", equalTo(stored));

        assertThat(customer.balanceOf(account)).isEqualByComparingTo(stored);
    }

    @Test
    @Requirement("DEP-02")
    void one_cent_over_the_deposit_limit_is_rejected_and_nothing_is_credited() {
        customer.api().deposit(account.id(), TestData.idempotencyKey(), DepositRequest.of("100000.01"))
                .then().statusCode(422)
                .body("code", equalTo("LIMIT_EXCEEDED"))
                .body("transferId", notNullValue());

        assertThat(customer.balanceOf(account)).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest
    @ArgumentsSource(InvalidAmounts.class)
    @Requirement("DEP-01")
    void an_invalid_amount_is_refused(Object amount, String issue) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);

        ApiError error = customer.api().deposit(account.id(), TestData.idempotencyKey(), body)
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.details()).containsExactly(new ApiError.Detail("amount", issue));
        assertThat(customer.balanceOf(account)).isEqualByComparingTo("0.00");
    }

    @Test
    @Requirement("DEP-01")
    void a_deposit_into_an_account_that_does_not_exist_is_not_found() {
        customer.api().deposit(UUID.randomUUID().toString(), TestData.idempotencyKey(), DepositRequest.of("10.00"))
                .then().statusCode(404)
                .body("code", equalTo("ACCOUNT_NOT_FOUND"));
    }
}
