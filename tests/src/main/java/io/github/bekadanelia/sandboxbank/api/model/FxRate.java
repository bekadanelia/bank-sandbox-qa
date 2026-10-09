package io.github.bekadanelia.sandboxbank.api.model;

import java.math.BigDecimal;
import java.time.Instant;

public record FxRate(Currency baseCurrency, Currency quoteCurrency, BigDecimal rate, Instant updatedAt) {
}
