package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.ApiError;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.Smoke;
import io.github.bekadanelia.sandboxbank.support.Iban;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@ApiTest
@Epic("Accounts")
@Feature("Opening accounts")
class AccountsTest {

    @ParameterizedTest
    @EnumSource(Currency.class)
    @Smoke
    @Requirement("ACC-01")
    void an_account_can_be_opened_in_every_supported_currency(Currency currency) {
        CustomerSession customer = Customers.registered();

        Account account = customer.api().openAccount(Map.of("currency", currency))
                .then().statusCode(201)
                .extract().as(Account.class);

        assertThat(account.currency()).isEqualTo(currency);
        assertThat(account.balance()).isEqualByComparingTo("0.00");
        assertThat(account.status()).isEqualTo(Account.Status.ACTIVE);
        assertThat(customer.account(account.id())).isEqualTo(account);
    }

    @ParameterizedTest
    @MethodSource("unsupportedCurrencies")
    @Requirement("ACC-01")
    void an_unsupported_currency_is_refused(Object currency, String issue) {
        Map<String, Object> body = new HashMap<>();
        body.put("currency", currency);

        ApiError error = Customers.registered().api().openAccount(body)
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.details()).containsExactly(new ApiError.Detail("currency", issue));
    }

    static Stream<Arguments> unsupportedCurrencies() {
        String oneOf = "must be one of GEL, USD, EUR";
        return Stream.of(
                argumentSet("a real currency the bank does not offer", "GBP", oneOf),
                argumentSet("a supported currency in lower case", "gel", oneOf),
                argumentSet("an empty string", "", oneOf),
                argumentSet("a number", 840, oneOf),
                argumentSet("null", null, "is required"));
    }

    @Test
    @Requirement("ACC-03")
    void account_numbers_are_unique_and_have_valid_check_digits() {
        CustomerSession customer = Customers.registered();

        List<String> numbers = Stream.of(Currency.GEL, Currency.GEL, Currency.USD)
                .map(currency -> customer.openAccount(currency).number())
                .toList();

        assertThat(numbers).doesNotHaveDuplicates();
        assertThat(numbers).allSatisfy(number -> {
            assertThat(number).matches("GE\\d{2}ZZ\\d{16}");
            assertThat(Iban.hasValidCheckDigits(number)).as("check digits of %s", number).isTrue();
        });
    }

    @Test
    @Requirement("ACC-02")
    void the_fifth_account_is_opened_and_the_sixth_is_refused() {
        CustomerSession customer = Customers.registered();
        for (int count = 1; count <= 5; count++) {
            customer.openAccount(Currency.GEL);
        }

        customer.api().openAccount(Map.of("currency", Currency.GEL))
                .then().statusCode(422)
                .body("code", equalTo("ACCOUNT_LIMIT_REACHED"));

        assertThat(customer.accounts()).hasSize(5);
    }

    @Test
    @Requirement("ACC-01")
    void accounts_are_listed_in_the_order_they_were_opened() {
        CustomerSession customer = Customers.registered();
        Account first = customer.openAccount(Currency.USD);
        Account second = customer.openAccount(Currency.GEL);
        Account third = customer.openAccount(Currency.EUR);

        assertThat(customer.accounts()).containsExactly(first, second, third);
    }

    @Test
    @Requirement("ACC-01")
    void a_new_customer_has_no_accounts() {
        Customers.registered().api().listAccounts()
                .then().statusCode(200)
                .body("items", empty());
    }

    @ParameterizedTest
    @MethodSource("idsOfNoAccount")
    @Requirement("ACC-04")
    void an_id_that_belongs_to_no_account_is_not_found(String accountId) {
        Customers.registered().api().getAccount(accountId)
                .then().statusCode(404)
                .body("code", equalTo("ACCOUNT_NOT_FOUND"));
    }

    static Stream<Arguments> idsOfNoAccount() {
        return Stream.of(
                argumentSet("a random UUID", UUID.randomUUID().toString()),
                argumentSet("not a UUID", "12345"),
                argumentSet("SQL in place of the id", "1' OR '1'='1"));
    }
}
