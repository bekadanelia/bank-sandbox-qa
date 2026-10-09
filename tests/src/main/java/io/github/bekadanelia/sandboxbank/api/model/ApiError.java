package io.github.bekadanelia.sandboxbank.api.model;

import java.util.List;

/** The single error shape of the API. {@code transferId} and {@code details} are optional. */
public record ApiError(String code, String message, String requestId, String transferId, List<Detail> details) {

    public record Detail(String field, String issue) {
    }

    /** Names of the fields the API complained about. */
    public List<String> fields() {
        return details == null ? List.of() : details.stream().map(Detail::field).toList();
    }
}
