package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bekadanelia.sandboxbank.api.BankApi;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.DepositRequest;
import io.github.bekadanelia.sandboxbank.api.model.TransferRequest;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.support.Concurrently;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Race conditions. Each test sends its requests at the same instant and then checks an outcome
 * that would be impossible if the bank handled them one after another correctly.
 */
@ApiTest
@Epic("Money movement")
@Feature("Concurrency")
@Severity(SeverityLevel.CRITICAL)
class ConcurrencyTest {

    private static final int REQUESTS = 12;

    private static List<Integer> statuses(List<Response> responses) {
        return responses.stream().map(Response::statusCode).sorted().toList();
    }

    @Test
    @Requirement({"TRF-10", "TRF-06"})
    void the_same_money_cannot_be_spent_twice() {
        CustomerSession customer = Customers.registered();
        Account source = customer.accountWithBalance(Currency.GEL, "100.00");
        Account target = customer.openAccount(Currency.GEL);

        List<Response> responses = Concurrently.repeat(REQUESTS,
                () -> customer.sendTransfer(source, target, "100.00"));

        assertThat(statuses(responses))
                .as("one transfer wins, the others find the account empty")
                .containsOnlyOnce(201)
                .containsOnly(201, 422);
        assertThat(responses).filteredOn(response -> response.statusCode() == 422)
                .allSatisfy(response -> assertThat(response.jsonPath().getString("code")).isEqualTo("INSUFFICIENT_FUNDS"));
        assertThat(customer.balanceOf(source)).isEqualByComparingTo("0.00");
        assertThat(customer.balanceOf(target)).isEqualByComparingTo("100.00");
    }

    @Test
    @Requirement("TRF-10")
    void simultaneous_deposits_into_one_account_all_count() {
        CustomerSession customer = Customers.registered();
        Account account = customer.openAccount(Currency.GEL);

        List<Response> responses = Concurrently.repeat(REQUESTS,
                () -> customer.api().deposit(account.id(), TestData.idempotencyKey(), DepositRequest.of("1.00")));

        assertThat(statuses(responses)).containsOnly(201);
        assertThat(customer.balanceOf(account)).as("no update was lost").isEqualByComparingTo("12.00");
    }

    /**
     * Regression test for a defect this suite found in the first version of the bank: deposits
     * of different customers deadlocked on the shared cash account and one of them got a 500.
     */
    @Test
    @Requirement("TRF-10")
    void deposits_of_different_customers_do_not_block_each_other() {
        List<CustomerSession> customers = IntStream.range(0, REQUESTS).mapToObj(index -> Customers.registered()).toList();
        List<Account> accounts = customers.stream().map(customer -> customer.openAccount(Currency.GEL)).toList();

        List<Response> responses = Concurrently.run(REQUESTS, index -> () -> customers.get(index).api()
                .deposit(accounts.get(index).id(), TestData.idempotencyKey(), DepositRequest.of("5.00")));

        assertThat(statuses(responses)).containsOnly(201);
    }

    @Test
    @Requirement("TRF-10")
    void transfers_in_opposite_directions_complete_and_conserve_the_total() {
        CustomerSession customer = Customers.registered();
        Account first = customer.accountWithBalance(Currency.GEL, "500.00");
        Account second = customer.accountWithBalance(Currency.GEL, "500.00");

        List<Response> responses = Concurrently.run(REQUESTS, index -> () -> index % 2 == 0
                ? customer.sendTransfer(first, second, "7.00")
                : customer.sendTransfer(second, first, "3.00"));

        assertThat(statuses(responses)).as("no deadlock, no timeout").containsOnly(201);
        assertThat(customer.balanceOf(first)).isEqualByComparingTo("476.00");
        assertThat(customer.balanceOf(second)).isEqualByComparingTo("524.00");
    }

    @Test
    @Requirement("IDM-02")
    void simultaneous_requests_with_one_key_create_one_transfer() {
        CustomerSession customer = Customers.registered();
        Account source = customer.accountWithBalance(Currency.GEL, "100.00");
        Account target = customer.openAccount(Currency.GEL);
        String key = TestData.idempotencyKey();
        TransferRequest request = TransferRequest.of(source, target, "10.00");

        List<Response> responses = Concurrently.repeat(REQUESTS, () -> customer.api().transfer(key, request));

        assertThat(statuses(responses)).containsOnly(201);
        assertThat(responses).extracting(response -> response.jsonPath().getString("id")).containsOnly(
                responses.getFirst().jsonPath().getString("id"));
        assertThat(responses).filteredOn(response -> response.header("Idempotency-Replayed") == null)
                .as("exactly one request did the work, the rest are replays")
                .hasSize(1);
        assertThat(customer.balanceOf(source)).isEqualByComparingTo("90.00");
    }

    @Test
    @Requirement("ACC-02")
    void simultaneous_account_openings_stop_at_the_limit_of_five() {
        CustomerSession customer = Customers.registered();
        BankApi api = customer.api();

        List<Response> responses = Concurrently.repeat(REQUESTS, () -> api.openAccount(Map.of("currency", "GEL")));

        assertThat(statuses(responses)).containsOnly(201, 422);
        assertThat(responses).filteredOn(response -> response.statusCode() == 201).hasSize(5);
        assertThat(customer.accounts()).hasSize(5);
    }
}
