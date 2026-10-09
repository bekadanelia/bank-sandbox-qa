package io.github.bekadanelia.sandboxbank.api.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Transfer(String id, Type type, Status status, String sourceAccountId, String targetAccountNumber,
                       BigDecimal sourceAmount, Currency sourceCurrency, BigDecimal targetAmount,
                       Currency targetCurrency, BigDecimal fxRate, String rejectionReason, String description,
                       Instant createdAt, Instant completedAt) {

    public enum Type {
        TRANSFER, DEPOSIT
    }

    public enum Status {
        COMPLETED, REJECTED
    }
}
