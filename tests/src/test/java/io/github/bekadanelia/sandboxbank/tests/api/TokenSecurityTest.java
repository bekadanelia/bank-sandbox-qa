package io.github.bekadanelia.sandboxbank.tests.api;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.github.bekadanelia.sandboxbank.api.BankApi;
import io.github.bekadanelia.sandboxbank.api.contract.OpenApiContract;
import io.github.bekadanelia.sandboxbank.api.contract.OpenApiContract.Endpoint;
import io.github.bekadanelia.sandboxbank.config.TestConfig;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.support.Jwt;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ApiTest
@Epic("Customers")
@Feature("Token security")
@Requirement("AUTH-05")
class TokenSecurityTest {

    private static CustomerSession customer;

    @BeforeAll
    static void registerCustomer() {
        customer = Customers.registered();
    }

    /**
     * The list of protected operations comes from the contract itself, so an endpoint added
     * tomorrow is covered without anyone remembering to extend this test.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("securedEndpoints")
    void every_protected_endpoint_refuses_a_request_without_a_token(Endpoint endpoint) {
        String path = endpoint.path().replaceAll("\\{[^}]+}", UUID.randomUUID().toString());

        BankApi.anonymous().request()
                .request(endpoint.method(), path)
                .then().statusCode(401)
                .body("code", equalTo("UNAUTHORIZED"));
    }

    static List<Endpoint> securedEndpoints() {
        return OpenApiContract.published().securedEndpoints();
    }

    @ParameterizedTest
    @MethodSource("tokensThatMustNotWork")
    void a_token_that_login_did_not_issue_is_refused(String authorization) {
        BankApi.anonymous().request()
                .header("Authorization", authorization)
                .get("/accounts")
                .then().statusCode(401)
                .body("code", equalTo("UNAUTHORIZED"));
    }

    static Stream<Arguments> tokensThatMustNotWork() {
        Instant now = Instant.now();
        Instant inAnHour = now.plus(1, ChronoUnit.HOURS);
        String genuine = customer.token();
        String[] parts = genuine.split("\\.");
        String otherCustomer = Customers.registered().token().split("\\.")[1];
        return Stream.of(
                argumentSet("not a token at all", "Bearer not.a.token"),
                argumentSet("empty bearer value", "Bearer "),
                argumentSet("basic authentication instead of bearer", "Basic ZGVtbzpkZW1v"),
                argumentSet("genuine token without the Bearer scheme", genuine),
                argumentSet("expired a minute ago", "Bearer " + Jwt.signed(customer.id(), TestConfig.jwtSecret(),
                        now.minus(31, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES))),
                argumentSet("signed with a different secret", "Bearer " + Jwt.signed(customer.id(),
                        "guessed-secret", now, inAnHour)),
                argumentSet("declares alg none and has no signature", "Bearer " + Jwt.unsigned(customer.id(),
                        now, inAnHour)),
                argumentSet("payload swapped for another customer's, signature kept",
                        "Bearer " + parts[0] + "." + otherCustomer + "." + parts[2]),
                argumentSet("signature cut off", "Bearer " + parts[0] + "." + parts[1] + "."),
                argumentSet("subject is not a customer id", "Bearer " + Jwt.signed("admin", TestConfig.jwtSecret(),
                        now, inAnHour)));
    }

    /**
     * Guards the test above. If a correctly signed, unexpired token built by the same helper were
     * also refused, the negative cases would pass for the wrong reason.
     */
    @Test
    void a_hand_built_token_with_the_right_secret_and_a_future_expiry_is_accepted() {
        Instant now = Instant.now();
        String token = Jwt.signed(customer.id(), TestConfig.jwtSecret(), now, now.plus(5, ChronoUnit.MINUTES));

        BankApi.withToken(token).currentCustomer()
                .then().statusCode(200)
                .body("id", equalTo(customer.id()));
    }

    @Test
    void a_valid_token_of_a_customer_that_does_not_exist_is_refused() {
        Instant now = Instant.now();
        String token = Jwt.signed(UUID.randomUUID().toString(), TestConfig.jwtSecret(), now,
                now.plus(5, ChronoUnit.MINUTES));

        BankApi.withToken(token).currentCustomer()
                .then().statusCode(401);
    }
}
