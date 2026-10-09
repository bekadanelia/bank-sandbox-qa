package io.github.bekadanelia.sandboxbank.api.contract;

/** The API answered with something openapi.yaml does not allow. */
public final class ContractViolation extends AssertionError {

    public ContractViolation(String message) {
        super(message);
    }
}
