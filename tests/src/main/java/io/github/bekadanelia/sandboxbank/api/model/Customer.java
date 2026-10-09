package io.github.bekadanelia.sandboxbank.api.model;

import java.time.Instant;

public record Customer(String id, String email, String fullName, Instant createdAt) {
}
