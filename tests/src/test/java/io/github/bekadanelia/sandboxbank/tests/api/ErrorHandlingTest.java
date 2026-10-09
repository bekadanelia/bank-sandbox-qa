package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.github.bekadanelia.sandboxbank.api.BankApi;
import io.github.bekadanelia.sandboxbank.api.model.ApiError;
import io.github.bekadanelia.sandboxbank.api.model.RegistrationRequest;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Whatever a client sends, the API answers with a client error in one shape and never with a 500. */
@ApiTest
@Epic("API conventions")
@Feature("Error handling")
class ErrorHandlingTest {

    private static CustomerSession customer;

    @BeforeAll
    static void registerCustomer() {
        customer = Customers.registered();
    }

    @Test
    @Requirement("API-03")
    void broken_json_is_reported_as_malformed() {
        BankApi.anonymous().request()
                .contentType(ContentType.JSON).body("{\"email\": \"cut off")
                .post("/auth/login")
                .then().statusCode(400)
                .body("code", equalTo("MALFORMED_JSON"));
    }

    @Test
    @Requirement("API-03")
    void a_json_value_that_is_not_an_object_is_reported_as_malformed() {
        BankApi.anonymous().request()
                .contentType(ContentType.JSON).body("\"just a string\"")
                .post("/auth/login")
                .then().statusCode(400)
                .body("code", equalTo("MALFORMED_JSON"));
    }

    @Test
    @Requirement("API-03")
    void a_json_array_in_place_of_an_object_fails_validation() {
        ApiError error = BankApi.anonymous().request()
                .contentType(ContentType.JSON).body("[]")
                .post("/auth/login")
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @Requirement("API-03")
    void a_body_that_is_not_json_is_an_unsupported_media_type() {
        BankApi.anonymous().request()
                .contentType(ContentType.URLENC).body("email=nino%40example.test&password=secret")
                .post("/auth/login")
                .then().statusCode(415)
                .body("code", equalTo("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @Requirement("API-03")
    void a_body_over_sixteen_kilobytes_is_refused() {
        RegistrationRequest oversized = new RegistrationRequest(
                TestData.uniqueEmail(), "N".repeat(17 * 1024), TestData.PASSWORD);

        BankApi.anonymous().register(oversized)
                .then().statusCode(413)
                .body("code", equalTo("PAYLOAD_TOO_LARGE"));
    }

    @Test
    @Requirement("API-03")
    void a_url_that_cannot_be_decoded_is_a_bad_request() {
        // %E0%A4 is the start of a three byte UTF-8 character with its last byte missing.
        customer.api().requestOutsideTheContract()
                .urlEncodingEnabled(false)
                .get("/accounts/%E0%A4")
                .then().statusCode(400)
                .body("code", equalTo("BAD_REQUEST"));
    }

    @Test
    @Requirement("API-02")
    void an_address_that_does_not_exist_is_not_found() {
        customer.api().request()
                .get("/cards")
                .then().statusCode(404)
                .body("code", equalTo("NOT_FOUND"));
    }

    @Test
    @Requirement("AUTH-05")
    void without_a_token_an_unknown_address_looks_like_any_protected_one() {
        BankApi.anonymous().request()
                .get("/cards")
                .then().statusCode(401)
                .body("code", equalTo("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @MethodSource("requestsThatFail")
    @Requirement("API-02")
    void every_error_has_the_same_shape_and_reveals_nothing_internal(int status, Function<BankApi, Response> call) {
        Response response = call.apply(customer.api());

        ApiError error = response.then().statusCode(status).extract().as(ApiError.class);
        assertThat(error.code()).matches("[A-Z][A-Z_]+");
        assertThat(error.message()).isNotBlank();
        assertThat(error.requestId()).isEqualTo(response.header("X-Request-Id"));
        assertThat(response.asString())
                .doesNotContainIgnoringCase("stack")
                .doesNotContain("node_modules", "SELECT ", "INSERT ", "postgres", "at async");
    }

    static Stream<Arguments> requestsThatFail() {
        String noSuchId = UUID.randomUUID().toString();
        return Stream.of(
                argumentSet("400 validation", 400, call(api -> api.openAccount(Map.of("currency", "XXX")))),
                argumentSet("404 account", 404, call(api -> api.getAccount(noSuchId))),
                argumentSet("404 transfer", 404, call(api -> api.getTransfer(noSuchId))),
                argumentSet("404 address", 404, call(api -> api.request().delete("/accounts"))),
                argumentSet("401 token", 401, call(api -> BankApi.withToken("forged").listAccounts())),
                argumentSet("409 duplicate email", 409, call(api -> BankApi.anonymous().register(
                        new RegistrationRequest(customer.email(), "Test Customer", TestData.PASSWORD)))));
    }

    private static Function<BankApi, Response> call(Function<BankApi, Response> call) {
        return call;
    }

    @Test
    @Requirement("API-02")
    void a_request_id_sent_by_the_client_is_used_in_the_answer() {
        String requestId = "checkout-" + UUID.randomUUID();

        customer.api().request()
                .header("X-Request-Id", requestId)
                .get("/accounts/{id}", UUID.randomUUID().toString())
                .then().statusCode(404)
                .header("X-Request-Id", requestId)
                .body("requestId", equalTo(requestId));
    }

    @Test
    @Requirement("API-02")
    void a_request_id_with_unsafe_characters_is_replaced() {
        String unsafe = "<script>alert(1)</script>";

        Response response = customer.api().request()
                .header("X-Request-Id", unsafe)
                .get("/accounts");

        response.then().statusCode(200);
        assertThat(response.header("X-Request-Id")).isNotEqualTo(unsafe).matches("[0-9a-f-]{36}");
    }
}
