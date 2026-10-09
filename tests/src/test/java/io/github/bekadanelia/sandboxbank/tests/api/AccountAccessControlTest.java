package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

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
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Broken object level authorization is the first item on the OWASP API Security Top 10.
 * Here one customer holds a valid token and tries every door to another customer's account.
 */
@ApiTest
@Epic("Accounts")
@Feature("Access control")
@Requirement("ACC-04")
@Severity(SeverityLevel.CRITICAL)
class AccountAccessControlTest {

    private static CustomerSession owner;
    private static CustomerSession stranger;
    private static Account account;
    private static Account strangersAccount;
    private static Transfer ownersDeposit;

    @BeforeAll
    static void twoCustomers() {
        owner = Customers.registered();
        account = owner.openAccount(Currency.GEL);
        ownersDeposit = owner.deposit(account, "100.00");
        stranger = Customers.registered();
        strangersAccount = stranger.accountWithBalance(Currency.GEL, "100.00");
    }

    @Test
    void a_stranger_cannot_read_the_account() {
        stranger.api().getAccount(account.id())
                .then().statusCode(404)
                .body("code", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void a_stranger_cannot_read_the_statement() {
        stranger.api().statement(account.id())
                .then().statusCode(404)
                .body("code", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void a_stranger_cannot_move_money_out_of_the_account() {
        stranger.api().transfer(TestData.idempotencyKey(), TransferRequest.of(account, strangersAccount, "50.00"))
                .then().statusCode(404)
                .body("code", equalTo("ACCOUNT_NOT_FOUND"));

        assertThat(owner.balanceOf(account)).isEqualByComparingTo("100.00");
        assertThat(stranger.balanceOf(strangersAccount)).isEqualByComparingTo("100.00");
    }

    @Test
    void a_stranger_cannot_deposit_through_the_owners_account_endpoint() {
        stranger.api().deposit(account.id(), TestData.idempotencyKey(), DepositRequest.of("10.00"))
                .then().statusCode(404);

        assertThat(owner.balanceOf(account)).isEqualByComparingTo("100.00");
    }

    @Test
    void a_stranger_cannot_freeze_the_account() {
        stranger.api().freeze(account.id())
                .then().statusCode(404);

        assertThat(owner.account(account.id()).status()).isEqualTo(Account.Status.ACTIVE);
    }

    @Test
    void a_stranger_cannot_read_a_transfer_the_owner_made() {
        stranger.api().getTransfer(ownersDeposit.id())
                .then().statusCode(404)
                .body("code", equalTo("TRANSFER_NOT_FOUND"));
    }

    @Test
    void a_foreign_account_is_indistinguishable_from_one_that_does_not_exist() {
        ApiError foreign = stranger.api().getAccount(account.id())
                .then().statusCode(404)
                .extract().as(ApiError.class);
        ApiError missing = stranger.api().getAccount(UUID.randomUUID().toString())
                .then().statusCode(404)
                .extract().as(ApiError.class);

        assertThat(foreign)
                .usingRecursiveComparison().ignoringFields("requestId")
                .isEqualTo(missing);
    }

    @Test
    void the_account_list_contains_only_the_callers_accounts() {
        assertThat(stranger.accounts()).extracting(Account::id).containsExactly(strangersAccount.id());
    }
}
