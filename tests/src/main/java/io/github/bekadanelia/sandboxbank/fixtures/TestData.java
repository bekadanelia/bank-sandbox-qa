package io.github.bekadanelia.sandboxbank.fixtures;

import io.github.bekadanelia.sandboxbank.api.model.RegistrationRequest;
import java.util.UUID;

/** Values that must be unique per test so that tests can run in parallel and in any order. */
public final class TestData {

    public static final String PASSWORD = "Tbilisi#2026";

    private TestData() {
    }

    public static String uniqueEmail() {
        return "qa." + UUID.randomUUID().toString().substring(0, 13) + "@example.test";
    }

    public static String idempotencyKey() {
        return UUID.randomUUID().toString();
    }

    public static RegistrationRequest registration() {
        return new RegistrationRequest(uniqueEmail(), "Test Customer", PASSWORD);
    }
}
