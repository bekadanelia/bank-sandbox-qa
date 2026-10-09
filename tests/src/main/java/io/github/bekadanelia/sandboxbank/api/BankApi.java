package io.github.bekadanelia.sandboxbank.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bekadanelia.sandboxbank.api.contract.ContractFilter;
import io.github.bekadanelia.sandboxbank.config.TestConfig;
import io.qameta.allure.Step;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.LogConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;
import java.util.Objects;

/**
 * Every endpoint of the Sandbox Bank API as one method.
 *
 * <p>Methods return the raw {@link Response} and assert nothing, so the same client serves
 * positive and negative tests. Bodies are {@code Object} on purpose: a test may pass a request
 * record, a map with a wrong type in it or a string of broken JSON.
 */
public final class BankApi {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    /** Unknown response fields fail deserialization, which is Jackson's default and what a test wants. */
    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private static final RestAssuredConfig CONFIG = RestAssuredConfig.config()
            .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                    .defaultObjectMapperType(ObjectMapperType.JACKSON_2)
                    .jackson2ObjectMapperFactory((type, charset) -> JSON))
            .httpClient(HttpClientConfig.httpClientConfig()
                    .setParam("http.connection.timeout", 5_000)
                    .setParam("http.socket.timeout", 30_000))
            .logConfig(LogConfig.logConfig()
                    // Also keeps the token out of the Allure attachments.
                    .blacklistHeader("Authorization")
                    .enableLoggingOfRequestAndResponseIfValidationFails());

    private final String token;

    private BankApi(String token) {
        this.token = token;
    }

    public static BankApi anonymous() {
        return new BankApi(null);
    }

    public static BankApi withToken(String token) {
        return new BankApi(Objects.requireNonNull(token, "token"));
    }

    /** A prepared request for the rare test that needs something the methods below cannot send. */
    public RequestSpecification request() {
        RequestSpecification request = requestOutsideTheContract();
        return TestConfig.contractChecks() ? request.filter(new ContractFilter()) : request;
    }

    /**
     * The same request without the contract check, for input so broken that it never reaches an
     * operation the contract describes, such as a URL that cannot be decoded.
     */
    public RequestSpecification requestOutsideTheContract() {
        RequestSpecification request = RestAssured.given()
                .config(CONFIG)
                .baseUri(TestConfig.apiUrl())
                .accept(ContentType.JSON)
                .filter(new AllureRestAssured());
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    private RequestSpecification jsonRequest(Object body) {
        return request().contentType(ContentType.JSON).body(body);
    }

    // ------------------------------------------------------------ customers

    @Step("Register a customer")
    public Response register(Object body) {
        return jsonRequest(body).post("/customers");
    }

    @Step("Log in")
    public Response login(Object body) {
        return jsonRequest(body).post("/auth/login");
    }

    @Step("Get the current customer")
    public Response currentCustomer() {
        return request().get("/customers/me");
    }

    // ------------------------------------------------------------ accounts

    @Step("Open an account")
    public Response openAccount(Object body) {
        return jsonRequest(body).post("/accounts");
    }

    @Step("List accounts")
    public Response listAccounts() {
        return request().get("/accounts");
    }

    @Step("Get account {accountId}")
    public Response getAccount(String accountId) {
        return request().get("/accounts/{accountId}", accountId);
    }

    @Step("Freeze account {accountId}")
    public Response freeze(String accountId) {
        return request().post("/accounts/{accountId}/freeze", accountId);
    }

    @Step("Unfreeze account {accountId}")
    public Response unfreeze(String accountId) {
        return request().post("/accounts/{accountId}/unfreeze", accountId);
    }

    @Step("Get the statement of account {accountId}")
    public Response statement(String accountId) {
        return statement(accountId, Map.of());
    }

    @Step("Get the statement of account {accountId} with {query}")
    public Response statement(String accountId, Map<String, ?> query) {
        return request().queryParams(query).get("/accounts/{accountId}/transactions", accountId);
    }

    // ------------------------------------------------------------ money movement

    @Step("Deposit into account {accountId}")
    public Response deposit(String accountId, String idempotencyKey, Object body) {
        return withKey(idempotencyKey, body).post("/accounts/{accountId}/deposits", accountId);
    }

    @Step("Create a transfer")
    public Response transfer(String idempotencyKey, Object body) {
        return withKey(idempotencyKey, body).post("/transfers");
    }

    @Step("Get transfer {transferId}")
    public Response getTransfer(String transferId) {
        return request().get("/transfers/{transferId}", transferId);
    }

    private RequestSpecification withKey(String idempotencyKey, Object body) {
        RequestSpecification request = jsonRequest(body);
        return idempotencyKey == null ? request : request.header(IDEMPOTENCY_KEY, idempotencyKey);
    }

    // ------------------------------------------------------------ reference data

    @Step("Get the FX rates")
    public Response fxRates() {
        return request().get("/fx-rates");
    }
}
