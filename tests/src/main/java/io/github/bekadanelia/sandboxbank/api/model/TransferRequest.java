package io.github.bekadanelia.sandboxbank.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Amounts travel as text, never as JSON numbers, so the request model keeps them as text too. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransferRequest(String sourceAccountId, String targetAccountNumber, String amount, String description) {

    public static TransferRequest of(Account source, Account target, String amount) {
        return new TransferRequest(source.id(), target.number(), amount, null);
    }

    public TransferRequest describedAs(String text) {
        return new TransferRequest(sourceAccountId, targetAccountNumber, amount, text);
    }
}
