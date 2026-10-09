package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.ApiError;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.Transaction;
import io.github.bekadanelia.sandboxbank.api.model.Transaction.Direction;
import io.github.bekadanelia.sandboxbank.api.model.TransactionPage;
import io.github.bekadanelia.sandboxbank.api.model.Transfer;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The statement is read only, so one account with a known history serves every test:
 * five deposits of 10.00, 20.00 ... 50.00 and then a transfer of 5.00 out.
 */
@ApiTest
@Epic("Accounts")
@Feature("Statement")
class StatementTest {

    private static CustomerSession customer;
    private static Account account;
    private static Transfer outgoing;

    @BeforeAll
    static void accountWithSixEntries() {
        customer = Customers.registered();
        account = customer.openAccount(Currency.GEL);
        for (String amount : List.of("10.00", "20.00", "30.00", "40.00", "50.00")) {
            customer.deposit(account, amount);
        }
        outgoing = customer.transfer(account, customer.openAccount(Currency.GEL), "5.00");
    }

    private TransactionPage page(Map<String, ?> query) {
        return customer.api().statement(account.id(), query).then().statusCode(200).extract().as(TransactionPage.class);
    }

    @Test
    @Requirement("STM-01")
    void entries_are_listed_newest_first_with_the_balance_after_each() {
        List<Transaction> entries = customer.statement(account).items();

        assertThat(entries)
                .extracting(Transaction::direction, entry -> entry.amount().toPlainString(),
                        entry -> entry.balanceAfter().toPlainString())
                .containsExactly(
                        tuple(Direction.DEBIT, "5.00", "145.00"),
                        tuple(Direction.CREDIT, "50.00", "150.00"),
                        tuple(Direction.CREDIT, "40.00", "100.00"),
                        tuple(Direction.CREDIT, "30.00", "60.00"),
                        tuple(Direction.CREDIT, "20.00", "30.00"),
                        tuple(Direction.CREDIT, "10.00", "10.00"));
        assertThat(entries).extracting(Transaction::createdAt).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @Requirement("STM-01")
    void the_newest_entry_shows_the_current_balance() {
        Transaction newest = customer.statement(account).items().getFirst();

        assertThat(newest.balanceAfter()).isEqualByComparingTo(customer.balanceOf(account));
    }

    @Test
    @Requirement("STM-01")
    void an_entry_names_the_transfer_it_belongs_to() {
        Transaction newest = customer.statement(account).items().getFirst();

        assertThat(newest.transferId()).isEqualTo(outgoing.id());
        assertThat(newest.type()).isEqualTo(Transfer.Type.TRANSFER);
        assertThat(newest.currency()).isEqualTo(Currency.GEL);
        assertThat(customer.statement(account).items().getLast().type()).isEqualTo(Transfer.Type.DEPOSIT);
    }

    @Test
    @Requirement("STM-02")
    void without_parameters_the_first_page_of_twenty_is_returned() {
        TransactionPage page = customer.statement(account);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalItems()).isEqualTo(6);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    @Requirement("STM-02")
    void pages_cover_every_entry_exactly_once_and_in_order() {
        List<BigDecimal> amounts = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            TransactionPage page = page(Map.of("page", index, "size", 4));
            assertThat(page.totalItems()).isEqualTo(6);
            assertThat(page.totalPages()).isEqualTo(2);
            page.items().forEach(entry -> amounts.add(entry.amount()));
        }

        assertThat(amounts).extracting(BigDecimal::toPlainString)
                .containsExactly("5.00", "50.00", "40.00", "30.00", "20.00", "10.00");
    }

    @Test
    @Requirement("STM-02")
    void a_page_past_the_end_is_empty_but_still_reports_the_totals() {
        customer.api().statement(account.id(), Map.of("page", 7, "size", 4))
                .then().statusCode(200)
                .body("items", empty())
                .body("page", equalTo(7))
                .body("totalItems", equalTo(6))
                .body("totalPages", equalTo(2));
    }

    @ParameterizedTest(name = "size {0} returns {1} entries")
    @MethodSource("pageSizesAtTheEdges")
    @Requirement("STM-02")
    void the_smallest_and_largest_page_size_are_accepted(int size, int expectedEntries) {
        customer.api().statement(account.id(), Map.of("size", size))
                .then().statusCode(200)
                .body("items", hasSize(expectedEntries))
                .body("size", equalTo(size));
    }

    static Stream<Arguments> pageSizesAtTheEdges() {
        return Stream.of(Arguments.of(1, 1), Arguments.of(100, 6));
    }

    @ParameterizedTest
    @MethodSource("invalidPaging")
    @Requirement("STM-02")
    void invalid_paging_parameters_are_refused(String parameter, String value) {
        ApiError error = customer.api().statement(account.id(), Map.of(parameter, value))
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.fields()).containsExactly(parameter);
    }

    static Stream<Arguments> invalidPaging() {
        return Stream.of(
                argumentSet("negative page", "page", "-1"),
                argumentSet("page that is not a number", "page", "first"),
                argumentSet("page beyond the supported range", "page", "100001"),
                argumentSet("size zero", "size", "0"),
                argumentSet("size above the maximum", "size", "101"),
                argumentSet("fractional size", "size", "2.5"),
                argumentSet("empty size", "size", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1; DROP TABLE ledger_entries", "1 OR 1=1"})
    @Requirement({"STM-02", "API-03"})
    void sql_in_a_paging_parameter_is_refused_as_a_bad_number(String injection) {
        customer.api().statement(account.id(), Map.of("size", injection))
                .then().statusCode(400);
    }

    @Test
    @Requirement("STM-01")
    void an_account_without_movements_has_an_empty_statement() {
        Account untouched = customer.openAccount(Currency.EUR);

        customer.api().statement(untouched.id())
                .then().statusCode(200)
                .body("items", empty())
                .body("totalItems", equalTo(0))
                .body("totalPages", equalTo(0));
    }
}
