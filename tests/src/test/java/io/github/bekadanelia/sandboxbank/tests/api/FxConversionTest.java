package io.github.bekadanelia.sandboxbank.tests.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.github.bekadanelia.sandboxbank.api.model.Account;
import io.github.bekadanelia.sandboxbank.api.model.Currency;
import io.github.bekadanelia.sandboxbank.api.model.FxRate;
import io.github.bekadanelia.sandboxbank.api.model.FxRateList;
import io.github.bekadanelia.sandboxbank.api.model.Transfer;
import io.github.bekadanelia.sandboxbank.fixtures.CustomerSession;
import io.github.bekadanelia.sandboxbank.fixtures.Customers;
import io.github.bekadanelia.sandboxbank.junit.ApiTest;
import io.github.bekadanelia.sandboxbank.junit.Requirement;
import io.github.bekadanelia.sandboxbank.junit.Smoke;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ApiTest
@Epic("Money movement")
@Feature("Currency conversion")
class FxConversionTest {

    private CustomerSession customer;

    @BeforeEach
    void registerCustomer() {
        customer = Customers.registered();
    }

    private List<FxRate> publishedRates() {
        return customer.api().fxRates().then().statusCode(200).extract().as(FxRateList.class).items();
    }

    private BigDecimal publishedRate(Currency from, Currency to) {
        return publishedRates().stream()
                .filter(rate -> rate.baseCurrency() == from && rate.quoteCurrency() == to)
                .findFirst().orElseThrow()
                .rate();
    }

    /**
     * Expected amounts were calculated by hand from the fixed sandbox rates:
     * GEL to USD 0.37, GEL to EUR 0.34, USD to EUR 0.915, USD to GEL 2.65, EUR to USD 1.0725, EUR to GEL 2.89.
     * The rows marked "exact half" are the ones that tell rounding half up from banker's rounding.
     */
    @ParameterizedTest(name = "{2} {0} becomes {3} {1} ({4})", quoteTextArguments = false)
    @CsvSource(textBlock = """
            GEL, USD,  100.00,    37.00, no rounding needed
            GEL, USD,   10.02,     3.71, 3.7074 rounds up
            EUR, USD,    0.02,     0.02, 0.02145 rounds down
            USD, EUR,    3.00,     2.75, exact half 2.745 rounds up
            GEL, EUR,    1.25,     0.43, exact half 0.425 rounds up
            EUR, USD,    2.00,     2.15, exact half 2.145 rounds up
            USD, GEL,    0.01,     0.03, smallest amount 0.0265 rounds up
            EUR, GEL,  123.45,   356.77, 356.7705 rounds down
            USD, GEL, 9999.99, 26499.97, large amount 26499.9735 rounds down
            """)
    @Smoke
    @Requirement({"FX-01", "FX-02"})
    void a_conversion_uses_the_published_rate_and_rounds_half_up(
            Currency from, Currency to, String amount, String expected, String why) {
        Account source = customer.accountWithBalance(from, "10000.00");
        Account target = customer.openAccount(to);

        Transfer transfer = customer.transfer(source, target, amount);

        assertThat(transfer.fxRate()).as("rate on the transfer").isEqualByComparingTo(publishedRate(from, to));
        assertThat(transfer.sourceAmount()).isEqualByComparingTo(amount);
        assertThat(transfer.targetAmount()).as(why).isEqualByComparingTo(expected);
        assertThat(customer.balanceOf(source))
                .isEqualByComparingTo(new BigDecimal("10000.00").subtract(new BigDecimal(amount)));
        assertThat(customer.balanceOf(target)).as(why).isEqualByComparingTo(expected);
    }

    @Test
    @Requirement("FX-03")
    void an_amount_that_converts_to_less_than_half_a_cent_is_rejected() {
        Account lari = customer.accountWithBalance(Currency.GEL, "1.00");
        Account dollars = customer.openAccount(Currency.USD);

        customer.sendTransfer(lari, dollars, "0.01")
                .then().statusCode(422)
                .body("code", equalTo("AMOUNT_TOO_SMALL"));

        assertThat(customer.balanceOf(lari)).isEqualByComparingTo("1.00");
        assertThat(customer.balanceOf(dollars)).isEqualByComparingTo("0.00");
    }

    @Test
    @Requirement({"FX-02", "FX-03"})
    void the_smallest_amount_that_converts_to_a_cent_is_sent() {
        Account lari = customer.accountWithBalance(Currency.GEL, "1.00");
        Account dollars = customer.openAccount(Currency.USD);

        customer.sendTransfer(lari, dollars, "0.02")
                .then().statusCode(201)
                .body("targetAmount", equalTo("0.01"));
    }

    @Test
    @Requirement("FX-01")
    void a_rate_is_published_for_every_pair_of_different_currencies() {
        List<FxRate> rates = publishedRates();

        assertThat(rates).hasSize(6);
        assertThat(rates).allSatisfy(rate -> {
            assertThat(rate.baseCurrency()).isNotEqualTo(rate.quoteCurrency());
            assertThat(rate.rate()).isPositive();
        });
        assertThat(rates).extracting(rate -> rate.baseCurrency() + "/" + rate.quoteCurrency()).doesNotHaveDuplicates();
    }

    @Test
    @Requirement("FX-04")
    void no_pair_of_rates_lets_a_customer_gain_by_converting_back_and_forth() {
        List<FxRate> rates = publishedRates();

        assertThat(rates).allSatisfy(there -> {
            BigDecimal back = publishedRate(there.quoteCurrency(), there.baseCurrency());
            assertThat(there.rate().multiply(back))
                    .as("%s to %s and back", there.baseCurrency(), there.quoteCurrency())
                    .isLessThan(BigDecimal.ONE);
        });
    }

    @Test
    @Requirement("FX-04")
    void a_round_trip_through_another_currency_returns_less_than_it_started_with() {
        Account lari = customer.accountWithBalance(Currency.GEL, "100.00");
        Account dollars = customer.openAccount(Currency.USD);

        customer.transfer(lari, dollars, "100.00");
        customer.transfer(dollars, lari, "37.00");

        assertThat(customer.balanceOf(lari)).isEqualByComparingTo("98.05");
        assertThat(customer.balanceOf(dollars)).isEqualByComparingTo("0.00");
    }
}
