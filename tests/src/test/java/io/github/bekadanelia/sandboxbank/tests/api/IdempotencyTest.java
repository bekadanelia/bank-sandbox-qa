package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.github.bekadanelia.sandboxbank.api.BankApi;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.ApiError;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.DepositRequest;
import io.github.bekadanelia.sandboxbank.api.model.Transfer;
import io.github.bekadanelia.sandboxbank.api.model.TransferRequest;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A payment client retries when it does not get an answer. The bank must make that safe:
 * the same key with the same request never moves money twice.
 */
@ApiTest
@Epic("Money movement")
@Feature("Idempotency")
@Severity(SeverityLevel.CRITICAL)
class IdempotencyTest {

    private static final String REPLAYED = "Idempotency-Replayed";

    private CustomerSession customer;
    private Account source;
    private Account target;

    @BeforeEach
    void customerWithOneHundredLari() {
        customer = Customers.registered();
        source = customer.accountWithBalance(Currency.GEL, "100.00");
        target = customer.openAccount(Currency.GEL);
    }

    @Test
    @Requirement("IDM-02")
    void a_repeated_transfer_returns_the_first_answer_and_moves_the_money_once() {
        String key = TestData.idempotencyKey();
        TransferRequest request = TransferRequest.of(source, target, "30.00");

        Response first = customer.api().transfer(key, request);
        Response second = customer.api().transfer(key, request);

        first.then().statusCode(201).header(REPLAYED, nullValue());
        second.then().statusCode(201).header(REPLAYED, equalTo("true"));
        assertThat(second.as(Transfer.class)).isEqualTo(first.as(Transfer.class));
        assertThat(customer.balanceOf(source)).isEqualByComparingTo("70.00");
        assertThat(customer.balanceOf(target)).isEqualByComparingTo("30.00");
        assertThat(customer.statement(source).totalItems()).as("deposit plus one transfer").isEqualTo(2);
    }

    @Test
    @Requirement("IDM-02")
    void a_repeated_deposit_is_credited_once() {
        String key = TestData.idempotencyKey();

        customer.api().deposit(target.id(), key, DepositRequest.of("25.00")).then().statusCode(201);
        customer.api().deposit(target.id(), key, DepositRequest.of("25.00"))
                .then().statusCode(201)
                .header(REPLAYED, equalTo("true"));

        assertThat(customer.balanceOf(target)).isEqualByComparingTo("25.00");
    }

    @Test
    @Requirement("IDM-02")
    void a_repeated_rejection_is_the_same_rejection() {
        String key = TestData.idempotencyKey();
        TransferRequest request = TransferRequest.of(source, target, "100.01");

        ApiError first = customer.api().transfer(key, request)
                .then().statusCode(422)
                .extract().as(ApiError.class);
        ApiError second = customer.api().transfer(key, request)
                .then().statusCode(422)
                .header(REPLAYED, equalTo("true"))
                .extract().as(ApiError.class);

        assertThat(second.code()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(second.transferId()).isEqualTo(first.transferId());
    }

    @Test
    @Requirement("IDM-02")
    void a_replay_does_not_look_at_the_current_balance_again() {
        String key = TestData.idempotencyKey();
        TransferRequest request = TransferRequest.of(source, target, "100.00");
        Transfer first = customer.api().transfer(key, request).then().statusCode(201).extract().as(Transfer.class);

        // The source account is empty now. A fresh attempt would be rejected, the replay is not.
        customer.api().transfer(key, request)
                .then().statusCode(201)
                .body("id", equalTo(first.id()));

        assertThat(customer.balanceOf(target)).isEqualByComparingTo("100.00");
    }

    @Test
    @Requirement("IDM-03")
    void a_key_used_again_for_a_different_request_is_a_conflict() {
        String key = TestData.idempotencyKey();
        customer.api().transfer(key, TransferRequest.of(source, target, "30.00")).then().statusCode(201);

        customer.api().transfer(key, TransferRequest.of(source, target, "31.00"))
                .then().statusCode(409)
                .body("code", equalTo("IDEMPOTENCY_KEY_REUSED"));

        assertThat(customer.balanceOf(source)).as("only the first transfer happened").isEqualByComparingTo("70.00");
    }

    @Test
    @Requirement("IDM-03")
    void a_transfer_key_cannot_be_used_again_for_a_deposit() {
        String key = TestData.idempotencyKey();
        customer.api().transfer(key, TransferRequest.of(source, target, "30.00")).then().statusCode(201);

        customer.api().deposit(target.id(), key, DepositRequest.of("30.00"))
                .then().statusCode(409)
                .body("code", equalTo("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    @Requirement("IDM-04")
    void two_customers_can_use_the_same_key_without_seeing_each_other() {
        String key = TestData.idempotencyKey();
        CustomerSession other = Customers.registered();
        Account othersAccount = other.openAccount(Currency.GEL);

        Transfer mine = customer.api().deposit(target.id(), key, DepositRequest.of("10.00"))
                .then().statusCode(201).header(REPLAYED, nullValue())
                .extract().as(Transfer.class);
        Transfer theirs = other.api().deposit(othersAccount.id(), key, DepositRequest.of("20.00"))
                .then().statusCode(201).header(REPLAYED, nullValue())
                .extract().as(Transfer.class);

        assertThat(theirs.id()).isNotEqualTo(mine.id());
        assertThat(other.balanceOf(othersAccount)).isEqualByComparingTo("20.00");
    }

    @Test
    @Requirement("IDM-01")
    void a_transfer_without_a_key_is_refused() {
        ApiError error = customer.api().transfer(null, TransferRequest.of(source, target, "30.00"))
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.details()).containsExactly(new ApiError.Detail(BankApi.IDEMPOTENCY_KEY, "header is required"));
        assertThat(customer.balanceOf(source)).isEqualByComparingTo("100.00");
    }

    @Test
    @Requirement("IDM-01")
    void a_deposit_without_a_key_is_refused() {
        customer.api().deposit(target.id(), null, DepositRequest.of("30.00"))
                .then().statusCode(400)
                .body("details[0].field", equalTo(BankApi.IDEMPOTENCY_KEY));
    }

    @ParameterizedTest
    @MethodSource("malformedKeys")
    @Requirement("IDM-01")
    void a_malformed_key_is_refused(String key) {
        customer.api().transfer(key, TransferRequest.of(source, target, "30.00"))
                .then().statusCode(400)
                .body("details[0].field", equalTo(BankApi.IDEMPOTENCY_KEY))
                .body("details[0].issue", equalTo("must be 8 to 64 letters, digits, hyphens or underscores"));
    }

    static Stream<Arguments> malformedKeys() {
        return Stream.of(
                argumentSet("7 characters, one too short", "k".repeat(7)),
                argumentSet("65 characters, one too long", "k".repeat(65)),
                argumentSet("contains a space", "key with spaces"),
                argumentSet("contains a character outside the allowed set", "key/with/slashes"));
    }

    @ParameterizedTest(name = "a key of {0} characters is accepted")
    @ValueSource(ints = {8, 64})
    @Requirement("IDM-01")
    void keys_at_both_ends_of_the_allowed_length_are_accepted(int length) {
        String key = TestData.idempotencyKey().replace("-", "").repeat(2).substring(0, length);

        customer.api().transfer(key, TransferRequest.of(source, target, "1.00"))
                .then().statusCode(201);
    }
}
