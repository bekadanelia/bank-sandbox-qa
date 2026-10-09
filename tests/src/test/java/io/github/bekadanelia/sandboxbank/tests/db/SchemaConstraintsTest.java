package io.github.bekadanelia.sandboxbank.tests.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.Transfer;
import io.github.bekadanelia.sandboxbank.db.Database;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.DbTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The database as the last line of defence. Application code can have bugs, so the rules that
 * protect money are also enforced by constraints. Each test attempts a bad write directly in
 * SQL and expects PostgreSQL to refuse it with a specific SQLSTATE.
 *
 * <p>Every attempt runs in a transaction that is rolled back, so nothing is left behind even if
 * a write is accepted by mistake.
 */
@DbTest
@Epic("Ledger")
@Feature("Database constraints")
class SchemaConstraintsTest {

    private static final String RESTRICT_VIOLATION = "23001";
    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";
    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    private static CustomerSession customer;
    private static Account account;
    private static Transfer deposit;

    @BeforeAll
    static void customerWithHistory() {
        customer = Customers.registered();
        account = customer.openAccount(Currency.GEL);
        deposit = customer.deposit(account, "100.00");
    }

    private static void assertRefused(Database database, String sqlState, String sql, Object... parameters)
            throws SQLException {
        database.inRolledBackTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) {
                    statement.setObject(index + 1, parameters[index]);
                }
                assertThatThrownBy(statement::executeUpdate)
                        .isInstanceOfSatisfying(SQLException.class, refusal ->
                                assertThat(refusal.getSQLState()).as(refusal.getMessage()).isEqualTo(sqlState));
            }
        });
    }

    @Test
    @Requirement("LED-05")
    void a_customer_balance_cannot_be_set_below_zero() throws SQLException {
        assertRefused(Database.owner(), CHECK_VIOLATION,
                "UPDATE accounts SET balance = -0.01 WHERE id = ?::uuid", account.id());
    }

    @Test
    @Requirement("LED-09")
    void a_ledger_entry_cannot_be_changed() throws SQLException {
        assertRefused(Database.owner(), RESTRICT_VIOLATION,
                "UPDATE ledger_entries SET amount = amount + 1 WHERE transfer_id = ?::uuid", deposit.id());
    }

    @Test
    @Requirement("LED-09")
    void a_ledger_entry_cannot_be_deleted() throws SQLException {
        assertRefused(Database.owner(), RESTRICT_VIOLATION,
                "DELETE FROM ledger_entries WHERE transfer_id = ?::uuid", deposit.id());
    }

    @Test
    @Requirement("LED-09")
    void a_ledger_entry_needs_a_positive_amount() throws SQLException {
        assertRefused(Database.owner(), CHECK_VIOLATION, """
                INSERT INTO ledger_entries (transfer_id, account_id, direction, amount, currency, balance_after)
                VALUES (?::uuid, ?::uuid, 'CREDIT', 0, 'GEL', 100)""", deposit.id(), account.id());
    }

    @Test
    @Requirement("LED-09")
    void a_ledger_entry_needs_an_existing_account() throws SQLException {
        assertRefused(Database.owner(), FOREIGN_KEY_VIOLATION, """
                INSERT INTO ledger_entries (transfer_id, account_id, direction, amount, currency, balance_after)
                VALUES (?::uuid, gen_random_uuid(), 'CREDIT', 1, 'GEL', 1)""", deposit.id());
    }

    @Test
    @Requirement("AUTH-01")
    void an_email_that_differs_only_in_case_cannot_be_inserted() throws SQLException {
        assertRefused(Database.owner(), UNIQUE_VIOLATION,
                "INSERT INTO customers (email, full_name, password_hash) VALUES (upper(?), 'Copy', 'x')",
                customer.email());
    }

    @Test
    @Requirement("ACC-03")
    void an_account_number_cannot_be_issued_twice() throws SQLException {
        assertRefused(Database.owner(), UNIQUE_VIOLATION,
                "INSERT INTO accounts (customer_id, currency, number) VALUES (?::uuid, 'GEL', ?)",
                customer.id(), account.number());
    }

    @Test
    @Requirement("ACC-01")
    void an_account_in_an_unsupported_currency_cannot_be_stored() throws SQLException {
        assertRefused(Database.owner(), CHECK_VIOLATION,
                "INSERT INTO accounts (customer_id, currency) VALUES (?::uuid, 'GBP')", customer.id());
    }

    @Test
    @Requirement("IDM-02")
    void an_idempotency_key_cannot_be_stored_twice_for_one_customer() throws SQLException {
        assertRefused(Database.owner(), UNIQUE_VIOLATION, """
                INSERT INTO transfers (kind, customer_id, idempotency_key, request_hash, source_account_id,
                                       target_account_id, source_amount, source_currency, target_currency)
                SELECT kind, customer_id, idempotency_key, 'another request', source_account_id,
                       target_account_id, 5, source_currency, target_currency
                FROM transfers
                WHERE id = ?::uuid""", deposit.id());
    }

    @Test
    @Requirement("TRF-08")
    void a_transfer_cannot_be_marked_rejected_without_a_reason() throws SQLException {
        assertRefused(Database.owner(), CHECK_VIOLATION,
                "UPDATE transfers SET status = 'REJECTED' WHERE id = ?::uuid", deposit.id());
    }

    @Test
    @Requirement("LED-06")
    void a_completed_transfer_cannot_lose_its_completion_time() throws SQLException {
        assertRefused(Database.owner(), CHECK_VIOLATION,
                "UPDATE transfers SET completed_at = NULL WHERE id = ?::uuid", deposit.id());
    }

    @Test
    @Requirement("FX-01")
    void an_fx_rate_cannot_be_zero_or_negative() throws SQLException {
        assertRefused(Database.owner(), CHECK_VIOLATION,
                "UPDATE fx_rates SET rate = 0 WHERE base_currency = 'GEL' AND quote_currency = 'USD'");
    }

    /** Guards the suite itself: the role that verification queries use must not be able to write. */
    @Test
    void the_read_only_role_of_the_test_suite_cannot_change_data() throws SQLException {
        assertRefused(Database.reader(), INSUFFICIENT_PRIVILEGE,
                "UPDATE accounts SET balance = 1000000 WHERE id = ?::uuid", account.id());
        assertRefused(Database.reader(), INSUFFICIENT_PRIVILEGE, "DELETE FROM audit_events");
    }
}
