package io.github.bekadanelia.sandboxbank.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/** One ledger entry as it appears on an account statement. */
public record Transaction(long id, String transferId, Transfer.Type type, Direction direction, BigDecimal amount,
                          Currency currency, BigDecimal balanceAfter, String description, Instant createdAt) {

    public enum Direction {
        DEBIT, CREDIT
    }
}
