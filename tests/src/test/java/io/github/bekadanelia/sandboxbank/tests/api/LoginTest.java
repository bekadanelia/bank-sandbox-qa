package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bekadanelia.sandboxbank.api.BankApi;
import io.github.bekadanelia.sandboxbank.api.model.ApiError;
import io.github.bekadanelia.sandboxbank.api.model.LoginRequest;
import io.github.bekadanelia.sandboxbank.api.model.Token;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.support.Jwt;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ApiTest
@Epic("Customers")
@Feature("Login")
class LoginTest {

    private static final int HALF_AN_HOUR = 1800;

    private static CustomerSession customer;

    private final BankApi api = BankApi.anonymous();

    @BeforeAll
    static void registerCustomer() {
        customer = Customers.registered();
    }

    @Test
    @Requirement("AUTH-03")
    void login_returns_a_bearer_token_that_identifies_the_customer() {
        Token token = api.login(new LoginRequest(customer.email(), customer.password()))
                .then().statusCode(200)
                .extract().as(Token.class);

        assertThat(token.tokenType()).isEqualTo("Bearer");
        BankApi.withToken(token.accessToken()).currentCustomer()
                .then().statusCode(200)
                .body("id", equalTo(customer.id()))
                .body("email", equalTo(customer.email()));
    }

    @Test
    @Requirement("AUTH-03")
    void the_token_expires_half_an_hour_after_it_was_issued() throws Exception {
        Token token = api.login(new LoginRequest(customer.email(), customer.password()))
                .then().statusCode(200)
                .extract().as(Token.class);

        JsonNode claims = new ObjectMapper().readTree(Jwt.payloadOf(token.accessToken()));
        assertThat(token.expiresIn()).isEqualTo(HALF_AN_HOUR);
        assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(HALF_AN_HOUR);
        assertThat(claims.get("sub").asText()).isEqualTo(customer.id());
    }

    @Test
    @Requirement("AUTH-03")
    void the_email_is_matched_without_regard_to_case() {
        api.login(new LoginRequest(customer.email().toUpperCase(Locale.ROOT), customer.password()))
                .then().statusCode(200);
    }

    @Test
    @Requirement("AUTH-04")
    void a_wrong_password_and_an_unknown_email_get_the_same_answer() {
        ApiError wrongPassword = api.login(new LoginRequest(customer.email(), customer.password() + "x"))
                .then().statusCode(401)
                .extract().as(ApiError.class);
        ApiError unknownEmail = api.login(new LoginRequest(TestData.uniqueEmail(), customer.password()))
                .then().statusCode(401)
                .extract().as(ApiError.class);

        assertThat(wrongPassword.code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknownEmail)
                .usingRecursiveComparison().ignoringFields("requestId")
                .isEqualTo(wrongPassword);
    }

    @Test
    @Requirement("AUTH-04")
    void the_password_is_case_sensitive() {
        api.login(new LoginRequest(customer.email(), customer.password().toUpperCase(Locale.ROOT)))
                .then().statusCode(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"' OR '1'='1", "' OR 1=1 --", "'; DROP TABLE customers; --", "\" OR \"\"=\""})
    @Requirement({"AUTH-04", "API-03"})
    void sql_injection_in_the_credentials_is_just_a_wrong_password(String injection) {
        api.login(new LoginRequest(customer.email(), injection))
                .then().statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
        api.login(new LoginRequest(injection, injection))
                .then().statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
    }

    @Test
    @Requirement("AUTH-03")
    void missing_credentials_are_a_validation_error_not_a_login_failure() {
        ApiError error = api.login(Map.of())
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.fields()).containsExactlyInAnyOrder("email", "password");
    }
}
