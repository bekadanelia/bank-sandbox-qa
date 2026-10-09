package io.github.bekadanelia.sandboxbank.api.model;

public record Token(String accessToken, String tokenType, int expiresIn) {
}
