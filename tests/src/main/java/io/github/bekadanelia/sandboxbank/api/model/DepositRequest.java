package io.github.bekadanelia.sandboxbank.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DepositRequest(String amount, String description) {

    public static DepositRequest of(String amount) {
        return new DepositRequest(amount, null);
    }
}
