package io.github.bekadanelia.sandboxbank.tests.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.ApiError;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.Transfer;
import io.github.bekadanelia.sandboxbank.api.model.TransferRequest;
import io.github.bekadanelia.sandboxbank.db.Database;
import io.github.bekadanelia.sandboxbank.db.Row;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.DbTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What the API reports against what the database holds. Each test acts through the API and
 * then reads the rows that action should have written.
 */
@DbTest
@Epic("Ledger")
@Feature("Persistence")
class PersistenceTest {

    private static final String ENTRIES_OF_TRANSFER = """
            SELECT a.kind, a.number, e.direction, e.amount, e.currency, e.balance_after
            FROM ledger_entries e
                     JOIN accounts a ON a.id = e.account_id
            WHERE e.transfer_id = ?::uuid
            ORDER BY e.id""";

    private final Database db = Database.reader();
    private CustomerSession customer;
    private Account lari;

    @BeforeEach
    void customerWithOneHundredLari() {
        customer = Customers.registered();
        lari = customer.accountWithBalance(Currency.GEL, "100.00");
    }

    @Test
    @Requirement({"TRF-01", "LED-01"})
    void a_transfer_is_stored_as_one_debit_and_one_credit() {
        Account target = customer.openAccount(Currency.GEL);

        Transfer transfer = customer.transfer(lari, target, "40.25");

        assertThat(db.query(ENTRIES_OF_TRANSFER, transfer.id()))
                .extracting(row -> row.text("number"), row -> row.text("direction"),
                        row -> row.text("amount"), row -> row.text("balance_after"))
                .containsExactly(
                        tuple(lari.number(), "DEBIT", "40.25", "59.75"),
                        tuple(target.number(), "CREDIT", "40.25", "40.25"));
    }

    @Test
    @Requirement({"FX-01", "LED-01"})
    void a_conversion_passes_through_the_banks_fx_position_in_both_currencies() {
        Account dollars = customer.openAccount(Currency.USD);

        Transfer transfer = customer.transfer(lari, dollars, "10.02");

        assertThat(db.query(ENTRIES_OF_TRANSFER, transfer.id()))
                .extracting(row -> row.text("kind"), row -> row.text("direction"),
                        row -> row.text("amount"), row -> row.text("currency"))
                .containsExactly(
                        tuple("CUSTOMER", "DEBIT", "10.02", "GEL"),
                        tuple("FX_POSITION", "CREDIT", "10.02", "GEL"),
                        tuple("FX_POSITION", "DEBIT", "3.71", "USD"),
                        tuple("CUSTOMER", "CREDIT", "3.71", "USD"));
    }

    @Test
    @Requirement({"DEP-01", "LED-04"})
    void a_deposit_is_paid_out_of_the_banks_cash_account() {
        Transfer deposit = customer.deposit(lari, "15.00");

        assertThat(db.query(ENTRIES_OF_TRANSFER, deposit.id()))
                .extracting(row -> row.text("kind"), row -> row.text("direction"), row -> row.text("amount"))
                .containsExactly(
                        tuple("CASH", "DEBIT", "15.00"),
                        tuple("CUSTOMER", "CREDIT", "15.00"));
    }

    @Test
    @Requirement("TRF-08")
    void a_rejected_transfer_is_stored_with_its_reason_and_without_ledger_entries() {
        Account target = customer.openAccount(Currency.GEL);

        ApiError rejection = customer.sendTransfer(lari, target, "100.01")
                .then().statusCode(422)
                .extract().as(ApiError.class);

        Row stored = db.one("SELECT status, rejection_reason, target_amount, completed_at FROM transfers WHERE id = ?::uuid",
                rejection.transferId());
        assertThat(stored.text("status")).isEqualTo("REJECTED");
        assertThat(stored.text("rejection_reason")).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(stored.get("target_amount")).isNull();
        assertThat(stored.get("completed_at")).isNull();
        assertThat(db.query(ENTRIES_OF_TRANSFER, rejection.transferId())).isEmpty();
    }

    @Test
    @Requirement("IDM-02")
    void a_replayed_request_is_stored_once() {
        Account target = customer.openAccount(Currency.GEL);
        String key = TestData.idempotencyKey();
        TransferRequest request = TransferRequest.of(lari, target, "1.00");

        customer.api().transfer(key, request).then().statusCode(201);
        customer.api().transfer(key, request).then().statusCode(201);

        Row stored = db.one(
                "SELECT count(*) AS transfers FROM transfers WHERE customer_id = ?::uuid AND kind = 'TRANSFER'",
                customer.id());
        assertThat(stored.number("transfers")).isEqualTo(1);
    }

    @Test
    @Requirement("AUD-01")
    void completed_and_rejected_transfers_are_audited_with_who_and_what() {
        Account target = customer.openAccount(Currency.GEL);
        Transfer completed = customer.transfer(lari, target, "60.00");
        String rejectedId = customer.sendTransfer(lari, target, "60.00")
                .then().statusCode(422)
                .extract().path("transferId");

        List<Row> events = db.query("""
                SELECT event_type, entity_id::text AS transfer_id, actor_id::text AS actor,
                       payload ->> 'sourceAmount' AS amount, payload ->> 'reason' AS reason
                FROM audit_events
                WHERE entity_type = 'TRANSFER' AND entity_id IN (?::uuid, ?::uuid)
                ORDER BY id""", completed.id(), rejectedId);

        assertThat(events)
                .extracting(row -> row.text("event_type"), row -> row.text("transfer_id"), row -> row.text("actor"),
                        row -> row.text("amount"), row -> row.text("reason"))
                .containsExactly(
                        tuple("TRANSFER_COMPLETED", completed.id(), customer.id(), "60.00", null),
                        tuple("TRANSFER_REJECTED", rejectedId, customer.id(), null, "INSUFFICIENT_FUNDS"));
    }

    @Test
    @Requirement("ACC-05")
    void freezing_an_account_twice_is_audited_once() {
        customer.freeze(lari);
        customer.freeze(lari);

        List<Row> events = db.query(
                "SELECT event_type FROM audit_events WHERE entity_type = 'ACCOUNT' AND entity_id = ?::uuid ORDER BY id",
                lari.id());

        assertThat(events).extracting(row -> row.text("event_type")).containsExactly("ACCOUNT_OPENED", "ACCOUNT_FROZEN");
    }

    @Test
    @Requirement("AUTH-06")
    void a_password_is_stored_as_a_salted_hash_and_never_as_text() {
        CustomerSession samePassword = Customers.registered();

        String hash = db.one("SELECT password_hash FROM customers WHERE id = ?::uuid", customer.id())
                .text("password_hash");
        String otherHash = db.one("SELECT password_hash FROM customers WHERE id = ?::uuid", samePassword.id())
                .text("password_hash");

        assertThat(customer.password()).isEqualTo(samePassword.password());
        assertThat(hash).startsWith("scrypt$").doesNotContain(customer.password());
        assertThat(hash).as("the salt makes equal passwords hash differently").isNotEqualTo(otherHash);
    }
}
