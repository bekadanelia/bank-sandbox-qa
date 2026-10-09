package io.github.bekadanelia.sandboxbank.fixtures;

import io.github.bekadanelia.sandboxbank.api.BankApi;
import io.github.bekadanelia.sandboxbank.api.model.Customer;
import io.github.bekadanelia.sandboxbank.api.model.LoginRequest;
import io.github.bekadanelia.sandboxbank.api.model.RegistrationRequest;
import io.github.bekadanelia.sandboxbank.api.model.Token;
import io.qameta.allure.Step;

/**
 * Creates customers through the public API.
 *
 * <p>Every test registers the customers it needs. Nothing is shared and nothing is cleaned up,
 * which is what makes the suite safe to run in parallel.
 */
public final class Customers {

    private Customers() {
    }

    @Step("Register a new customer and sign in")
    public static CustomerSession registered() {
        return registered(TestData.registration());
    }

    @Step("Register a new customer and sign in")
    public static CustomerSession registered(RegistrationRequest registration) {
        BankApi.anonymous().register(registration)
                .then().statusCode(201);
        return signedIn(registration.email(), registration.password());
    }

    @Step("Sign in as {email}")
    public static CustomerSession signedIn(String email, String password) {
        Token token = BankApi.anonymous().login(new LoginRequest(email, password))
                .then().statusCode(200)
                .extract().as(Token.class);
        Customer customer = BankApi.withToken(token.accessToken()).currentCustomer()
                .then().statusCode(200)
                .extract().as(Customer.class);
        return new CustomerSession(customer, password, token.accessToken());
    }
}
