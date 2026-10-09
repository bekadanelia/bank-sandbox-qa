package io.github.bekadanelia.sandboxbank.api.model;

import java.util.List;

public record TransactionPage(List<Transaction> items, int page, int size, int totalItems, int totalPages) {
}
