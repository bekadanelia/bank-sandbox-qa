package io.github.bekadanelia.sandboxbank.fixtures;

import io.github.bekadanelia.sandboxbank.api.BankApi;
import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.AccountList;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.Customer;
import io.github.bekadanelia.sandboxbank.api.model.DepositRequest;
import io.github.bekadanelia.sandboxbank.api.model.TransactionPage;
import io.github.bekadanelia.sandboxbank.api.model.Transfer;
import io.github.bekadanelia.sandboxbank.api.model.TransferRequest;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A signed in customer and the things tests most often do on its behalf.
 *
 * <p>Methods that return a model expect success and fail the test otherwise. They exist for
 * arranging a scenario. The behaviour under test goes through {@link #api()} or the
 * {@code send...} methods, which return the raw response.
 */
public final class CustomerSession {

    private final Customer customer;
    private final String password;
    private final String token;
    private final BankApi api;

    CustomerSession(Customer customer, String password, String token) {
        this.customer = customer;
        this.password = password;
        this.token = token;
        this.api = BankApi.withToken(token);
    }

    public Customer customer() {
        return customer;
    }

    public String id() {
        return customer.id();
    }

    public String email() {
        return customer.email();
    }

    public String password() {
        return password;
    }

    public String token() {
        return token;
    }

    public BankApi api() {
        return api;
    }

    // ------------------------------------------------------------ arranging

    @Step("Open a {currency} account")
    public Account openAccount(Currency currency) {
        return api.openAccount(Map.of("currency", currency))
                .then().statusCode(201)
                .extract().as(Account.class);
    }

    @Step("Open a {currency} account holding {balance}")
    public Account accountWithBalance(Currency currency, String balance) {
        Account account = openAccount(currency);
        deposit(account, balance);
        return account(account.id());
    }

    @Step("Deposit {amount}")
    public Transfer deposit(Account account, String amount) {
        return api.deposit(account.id(), TestData.idempotencyKey(), DepositRequest.of(amount))
                .then().statusCode(201)
                .extract().as(Transfer.class);
    }

    @Step("Transfer {amount}")
    public Transfer transfer(Account source, Account target, String amount) {
        return sendTransfer(source, target, amount)
                .then().statusCode(201)
                .extract().as(Transfer.class);
    }

    @Step("Freeze the account")
    public Account freeze(Account account) {
        return api.freeze(account.id()).then().statusCode(200).extract().as(Account.class);
    }

    // ------------------------------------------------------------ acting

    /** Sends a transfer under a fresh idempotency key and leaves the verdict to the test. */
    public Response sendTransfer(Account source, Account target, String amount) {
        return api.transfer(TestData.idempotencyKey(), TransferRequest.of(source, target, amount));
    }

    // ------------------------------------------------------------ observing

    public Account account(String accountId) {
        return api.getAccount(accountId).then().statusCode(200).extract().as(Account.class);
    }

    public BigDecimal balanceOf(Account account) {
        return account(account.id()).balance();
    }

    public List<Account> accounts() {
        return api.listAccounts().then().statusCode(200).extract().as(AccountList.class).items();
    }

    public TransactionPage statement(Account account) {
        return api.statement(account.id()).then().statusCode(200).extract().as(TransactionPage.class);
    }

    public Transfer transferById(String transferId) {
        return api.getTransfer(transferId).then().statusCode(200).extract().as(Transfer.class);
    }
}
