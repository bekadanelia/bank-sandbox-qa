package io.github.bekadanelia.sandboxbank.api.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Account(String id, String number, Currency currency, BigDecimal balance, Status status,
                      Instant createdAt) {

    public enum Status {
        ACTIVE, FROZEN
    }
}
