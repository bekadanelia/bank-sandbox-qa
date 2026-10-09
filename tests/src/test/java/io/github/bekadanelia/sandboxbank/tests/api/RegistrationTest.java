package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.github.bekadanelia.sandboxbank.api.BankApi;
import io.github.bekadanelia.sandboxbank.api.model.ApiError;
import io.github.bekadanelia.sandboxbank.api.model.Customer;
import io.github.bekadanelia.sandboxbank.api.model.LoginRequest;
import io.github.bekadanelia.sandboxbank.api.model.RegistrationRequest;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.Smoke;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ApiTest
@Epic("Customers")
@Feature("Registration")
class RegistrationTest {

    private final BankApi api = BankApi.anonymous();

    @Test
    @Smoke
    @Requirement("AUTH-01")
    void a_new_customer_is_registered_and_can_sign_in() {
        RegistrationRequest registration = TestData.registration();

        Customer customer = api.register(registration)
                .then().statusCode(201)
                .extract().as(Customer.class);

        assertThat(customer.id()).isNotBlank();
        assertThat(customer.email()).isEqualTo(registration.email());
        assertThat(customer.fullName()).isEqualTo(registration.fullName());
        assertThat(customer.createdAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        api.login(new LoginRequest(registration.email(), registration.password()))
                .then().statusCode(200);
    }

    @Test
    @Requirement("AUTH-01")
    void an_email_is_accepted_only_once_whatever_its_case() {
        RegistrationRequest first = TestData.registration();
        api.register(first).then().statusCode(201);

        api.register(first.withEmail(first.email().toUpperCase(Locale.ROOT)))
                .then().statusCode(409)
                .body("code", equalTo("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @Requirement("AUTH-01")
    void a_name_in_georgian_script_comes_back_unchanged() {
        RegistrationRequest registration = new RegistrationRequest(
                TestData.uniqueEmail(), "ნინო ბერიძე", TestData.PASSWORD);

        api.register(registration)
                .then().statusCode(201)
                .body("fullName", equalTo("ნინო ბერიძე"));
    }

    @ParameterizedTest
    @MethodSource("valuesOnTheEdgeOfTheAllowedRange")
    @Requirement("AUTH-02")
    void values_on_the_edge_of_the_allowed_range_are_accepted(String fullName, String password) {
        api.register(new RegistrationRequest(TestData.uniqueEmail(), fullName, password))
                .then().statusCode(201);
    }

    static Stream<Arguments> valuesOnTheEdgeOfTheAllowedRange() {
        return Stream.of(
                argumentSet("shortest name, 2 characters", "Li", TestData.PASSWORD),
                argumentSet("longest name, 100 characters", "N".repeat(100), TestData.PASSWORD),
                argumentSet("shortest password, 8 characters", "Test Customer", "p".repeat(8)),
                argumentSet("longest password, 72 characters", "Test Customer", "p".repeat(72)));
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    @Requirement("AUTH-02")
    void invalid_input_is_refused_and_the_problem_is_named(String field, Object value, String issue) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", TestData.uniqueEmail());
        body.put("fullName", "Test Customer");
        body.put("password", TestData.PASSWORD);
        body.put(field, value);

        ApiError error = api.register(body)
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.details()).containsExactly(new ApiError.Detail(field, issue));
    }

    static Stream<Arguments> invalidRegistrations() {
        String validEmail = "must be a valid email address";
        return Stream.of(
                argumentSet("email is null", "email", null, "is required"),
                argumentSet("email without @", "email", "nino.example.test", validEmail),
                argumentSet("email without a domain dot", "email", "nino@example", validEmail),
                argumentSet("email with a space", "email", "nino beridze@example.test", validEmail),
                argumentSet("email of 255 characters", "email", "n".repeat(243) + "@example.test",
                        "must be 3 to 254 characters long"),
                argumentSet("email as a number", "email", 42, "must be a string"),
                argumentSet("name of 1 character", "fullName", "N", "must be 2 to 100 characters long"),
                argumentSet("name of 101 characters", "fullName", "N".repeat(101), "must be 2 to 100 characters long"),
                argumentSet("name with a NUL character", "fullName", "Nino\u0000Beridze",
                        "must not contain control characters"),
                argumentSet("password of 7 characters", "password", "p".repeat(7), "must be 8 to 72 characters long"),
                argumentSet("password of 73 characters", "password", "p".repeat(73), "must be 8 to 72 characters long"),
                argumentSet("password as a boolean", "password", true, "must be a string"));
    }

    @Test
    @Requirement("AUTH-02")
    void every_problem_is_reported_in_one_response() {
        ApiError error = api.register(Map.of())
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.fields()).containsExactlyInAnyOrder("email", "fullName", "password");
    }
}
