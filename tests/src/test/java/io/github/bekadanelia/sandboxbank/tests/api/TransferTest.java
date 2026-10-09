package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.ApiError;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.Transaction;
import io.github.bekadanelia.sandboxbank.api.model.Transfer;
import io.github.bekadanelia.sandboxbank.api.model.TransferRequest;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.fixtures.TestData;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.Smoke;
import io.github.bekadanelia.sandboxbank.support.Iban;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ApiTest
@Epic("Money movement")
@Feature("Transfers")
class TransferTest {

    private CustomerSession sender;
    private Account source;

    @BeforeEach
    void senderWithOneHundredLari() {
        sender = Customers.registered();
        source = sender.accountWithBalance(Currency.GEL, "100.00");
    }

    @Test
    @Smoke
    @Requirement("TRF-01")
    void a_transfer_between_own_accounts_moves_exactly_the_amount() {
        Account target = sender.openAccount(Currency.GEL);

        Transfer transfer = sender.api()
                .transfer(TestData.idempotencyKey(), TransferRequest.of(source, target, "40.25"))
                .then().statusCode(201)
                .extract().as(Transfer.class);

        assertThat(transfer.type()).isEqualTo(Transfer.Type.TRANSFER);
        assertThat(transfer.status()).isEqualTo(Transfer.Status.COMPLETED);
        assertThat(transfer.sourceAccountId()).isEqualTo(source.id());
        assertThat(transfer.targetAccountNumber()).isEqualTo(target.number());
        assertThat(transfer.sourceAmount()).isEqualByComparingTo("40.25");
        assertThat(transfer.targetAmount()).isEqualByComparingTo("40.25");
        assertThat(transfer.fxRate()).as("no conversion inside one currency").isNull();
        assertThat(transfer.rejectionReason()).isNull();
        assertThat(transfer.completedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        assertThat(sender.balanceOf(source)).isEqualByComparingTo("59.75");
        assertThat(sender.balanceOf(target)).isEqualByComparingTo("40.25");
    }

    @Test
    @Requirement("TRF-01")
    void a_transfer_to_another_customer_arrives_with_its_description() {
        CustomerSession recipient = Customers.registered();
        Account target = recipient.openAccount(Currency.GEL);

        sender.api().transfer(TestData.idempotencyKey(),
                        TransferRequest.of(source, target, "15.00").describedAs("Rent for October"))
                .then().statusCode(201);

        assertThat(recipient.balanceOf(target)).isEqualByComparingTo("15.00");
        Transaction received = recipient.statement(target).items().getFirst();
        assertThat(received.direction()).isEqualTo(Transaction.Direction.CREDIT);
        assertThat(received.amount()).isEqualByComparingTo("15.00");
        assertThat(received.description()).isEqualTo("Rent for October");
    }

    @Test
    @Requirement({"TRF-01", "TRF-06"})
    void the_whole_balance_can_be_sent_and_leaves_zero() {
        Account target = sender.openAccount(Currency.GEL);

        sender.transfer(source, target, "100.00");

        assertThat(sender.balanceOf(source)).isEqualByComparingTo("0.00");
        assertThat(sender.balanceOf(target)).isEqualByComparingTo("100.00");
    }

    @Test
    @Requirement("TRF-01")
    void a_completed_transfer_can_be_read_back_unchanged() {
        Transfer created = sender.transfer(source, sender.openAccount(Currency.GEL), "1.00");

        assertThat(sender.transferById(created.id())).isEqualTo(created);
    }

    @Test
    @Requirement("TRF-08")
    void a_rejected_transfer_is_stored_with_its_reason_and_moves_no_money() {
        Account target = sender.openAccount(Currency.GEL);

        ApiError rejection = sender.sendTransfer(source, target, "100.01")
                .then().statusCode(422)
                .extract().as(ApiError.class);

        Transfer stored = sender.transferById(rejection.transferId());
        assertThat(stored.status()).isEqualTo(Transfer.Status.REJECTED);
        assertThat(stored.rejectionReason()).isEqualTo("INSUFFICIENT_FUNDS").isEqualTo(rejection.code());
        assertThat(stored.sourceAmount()).isEqualByComparingTo("100.01");
        assertThat(stored.targetAmount()).isNull();
        assertThat(stored.completedAt()).isNull();
        assertThat(sender.balanceOf(source)).isEqualByComparingTo("100.00");
        assertThat(sender.balanceOf(target)).isEqualByComparingTo("0.00");
        assertThat(sender.statement(target).items()).isEmpty();
    }

    @Test
    @Requirement("TRF-09")
    void a_well_formed_number_of_no_account_is_reported_before_anything_is_stored() {
        TransferRequest request = new TransferRequest(source.id(), Iban.unissued(), "10.00", null);

        sender.api().transfer(TestData.idempotencyKey(), request)
                .then().statusCode(422)
                .body("code", equalTo("TARGET_ACCOUNT_NOT_FOUND"))
                .body("$", not(hasKey("transferId")));

        assertThat(sender.balanceOf(source)).isEqualByComparingTo("100.00");
    }

    @ParameterizedTest
    @MethodSource("brokenAccountNumbers")
    @Requirement("TRF-09")
    void a_mistyped_account_number_is_caught_before_the_lookup(String number, String issue) {
        ApiError error = sender.api()
                .transfer(TestData.idempotencyKey(), new TransferRequest(source.id(), number, "10.00", null))
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.details()).containsExactly(new ApiError.Detail("targetAccountNumber", issue));
    }

    static Stream<Arguments> brokenAccountNumbers() {
        String genuine = Customers.registered().openAccount(Currency.GEL).number();
        String layout = "must look like GE00ZZ0000000000000000";
        String checkDigits = "has invalid check digits";
        char lastDigit = genuine.charAt(21);
        String oneDigitMistyped = genuine.substring(0, 21) + (lastDigit == '9' ? '0' : (char) (lastDigit + 1));
        return Stream.of(
                argumentSet("wrong check digits", Iban.withWrongCheckDigits(genuine), checkDigits),
                argumentSet("one digit mistyped", oneDigitMistyped, checkDigits),
                argumentSet("one digit too short", genuine.substring(0, 21), layout),
                argumentSet("one digit too long", genuine + "0", layout),
                argumentSet("lower case", genuine.toLowerCase(), layout),
                argumentSet("another bank code", genuine.replace("ZZ", "NB"), layout),
                argumentSet("spaces between groups", genuine.replaceAll("(.{4})", "$1 ").trim(), layout),
                argumentSet("empty", "", "must be 1 to 34 characters long"));
    }

    @Test
    @Requirement("ACC-04")
    void a_source_account_that_does_not_exist_is_not_found() {
        Account target = sender.openAccount(Currency.GEL);

        sender.api().transfer(TestData.idempotencyKey(),
                        new TransferRequest(UUID.randomUUID().toString(), target.number(), "10.00", null))
                .then().statusCode(404)
                .body("code", equalTo("ACCOUNT_NOT_FOUND"));
    }

    @ParameterizedTest
    @MethodSource("descriptions")
    @Requirement("TRF-01")
    void the_description_is_stored_exactly_as_sent(String description) {
        Account target = sender.openAccount(Currency.GEL);

        Transfer transfer = sender.api()
                .transfer(TestData.idempotencyKey(), TransferRequest.of(source, target, "1.00").describedAs(description))
                .then().statusCode(201)
                .extract().as(Transfer.class);

        assertThat(transfer.description()).isEqualTo(description);
        assertThat(sender.statement(target).items().getFirst().description()).isEqualTo(description);
    }

    static Stream<Arguments> descriptions() {
        return Stream.of(
                argumentSet("the longest allowed, 140 characters", "d".repeat(140)),
                argumentSet("Georgian script", "ბინის ქირა"),
                argumentSet("HTML that must stay text", "<img src=x onerror=alert(1)>"),
                argumentSet("SQL that must stay text", "'); DROP TABLE transfers; --"));
    }

    @Test
    @Requirement("TRF-02")
    void a_field_the_api_does_not_know_is_ignored() {
        Account target = sender.openAccount(Currency.GEL);
        Map<String, Object> body = Map.of(
                "sourceAccountId", source.id(),
                "targetAccountNumber", target.number(),
                "amount", "1.00",
                "fee", "0.00");

        sender.api().transfer(TestData.idempotencyKey(), body)
                .then().statusCode(201);

        assertThat(sender.balanceOf(source)).isEqualByComparingTo("99.00");
    }

    @Test
    @Requirement("TRF-02")
    void a_description_of_141_characters_is_refused() {
        Account target = sender.openAccount(Currency.GEL);

        ApiError error = sender.api()
                .transfer(TestData.idempotencyKey(), TransferRequest.of(source, target, "1.00").describedAs("d".repeat(141)))
                .then().statusCode(400)
                .extract().as(ApiError.class);

        assertThat(error.fields()).containsExactly("description");
    }
}
