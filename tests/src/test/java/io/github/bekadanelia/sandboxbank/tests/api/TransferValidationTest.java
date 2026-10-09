package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.ApiError;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Input validation of the transfer request. No request in this class may move money, so all
 * tests share one customer and each one checks that the balance is still what it was.
 */
@ApiTest
@Epic("Money movement")
@Feature("Transfer validation")
@Requirement("TRF-02")
class TransferValidationTest {

    private static CustomerSession customer;
    private static Account source;
    private static Account target;

    @BeforeAll
    static void customerWithTwoAccounts() {
        customer = Customers.registered();
        source = customer.accountWithBalance(Currency.GEL, "100.00");
        target = customer.openAccount(Currency.GEL);
    }

    private static Map<String, Object> validRequest() {
        Map<String, Object> body = new HashMap<>();
        body.put("sourceAccountId", source.id());
        body.put("targetAccountNumber", target.number());
        body.put("amount", "10.00");
        return body;
    }

    @ParameterizedTest
    @ArgumentsSource(InvalidAmounts.class)
    void an_invalid_amount_is_refused(Object amount, String issue) {
        Map<String, Object> body = validRequest();
        body.put("amount", amount);

        ApiError error = customer.api().transfer(TestData.idempotencyKey(), body)
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.details()).containsExactly(new ApiError.Detail("amount", issue));
        assertThat(customer.balanceOf(source)).isEqualByComparingTo("100.00");
    }

    @ParameterizedTest
    @MethodSource("invalidSourceAccountIds")
    void a_source_account_id_that_is_not_a_uuid_is_refused(Object sourceAccountId, String issue) {
        Map<String, Object> body = validRequest();
        body.put("sourceAccountId", sourceAccountId);

        ApiError error = customer.api().transfer(TestData.idempotencyKey(), body)
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.details()).containsExactly(new ApiError.Detail("sourceAccountId", issue));
        assertThat(customer.balanceOf(source)).isEqualByComparingTo("100.00");
    }

    static Stream<Arguments> invalidSourceAccountIds() {
        return Stream.of(
                argumentSet("the account number instead of the id", source.number(), "must be a UUID"),
                argumentSet("a number", 1, "must be a UUID"),
                argumentSet("null", null, "is required"));
    }

    @Test
    void all_problems_of_a_request_are_reported_together() {
        ApiError error = customer.api().transfer(null, Map.of("amount", "0"))
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.fields())
                .containsExactlyInAnyOrder("Idempotency-Key", "sourceAccountId", "targetAccountNumber", "amount");
    }
}
