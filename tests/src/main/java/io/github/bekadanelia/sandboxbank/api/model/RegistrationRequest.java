package io.github.bekadanelia.sandboxbank.api.model;

public record RegistrationRequest(String email, String fullName, String password) {

    public RegistrationRequest withEmail(String newEmail) {
        return new RegistrationRequest(newEmail, fullName, password);
    }
}
