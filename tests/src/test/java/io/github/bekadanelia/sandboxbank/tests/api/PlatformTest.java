package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;

import io.github.bekadanelia.sandboxbank.api.contract.OpenApiContract;
import io.github.bekadanelia.sandboxbank.config.TestConfig;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.Smoke;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

/** Things that are true of the service as a whole: health, the published contract, response headers. */
@ApiTest
@Epic("API conventions")
@Feature("Platform")
class PlatformTest {

    @Test
    @Smoke
    void the_bank_reports_itself_healthy() {
        RestAssured.given().baseUri(TestConfig.baseUrl())
                .get("/health")
                .then().statusCode(200)
                .body("status", equalTo("UP"))
                .body("version", matchesPattern("\\d+\\.\\d+\\.\\d+"));
    }

    @Test
    @Smoke
    @Requirement("API-01")
    void the_published_contract_describes_the_money_endpoints() {
        assertThat(OpenApiContract.published().documentedResponses())
                .contains("POST /transfers 201", "POST /transfers 422", "POST /accounts/{accountId}/deposits 201");
    }

    @Test
    @Requirement("API-04")
    void api_responses_forbid_caching_and_content_sniffing_and_hide_the_server_technology() {
        Customers.registered().api().listAccounts()
                .then().statusCode(200)
                .header("Cache-Control", equalTo("no-store"))
                .header("X-Content-Type-Options", equalTo("nosniff"))
                .header("X-Powered-By", nullValue());
    }

    @Test
    @Requirement("API-04")
    void the_web_client_is_served_with_a_content_security_policy_that_allows_only_its_own_scripts() {
        RestAssured.given().baseUri(TestConfig.baseUrl())
                .get("/")
                .then().statusCode(200)
                .header("Content-Security-Policy", containsString("default-src 'self'"))
                .header("Content-Security-Policy", containsString("frame-ancestors 'none'"))
                .header("X-Frame-Options", equalTo("DENY"));
    }
}
