package io.github.bekadanelia.sandboxbank.tests.api;

import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.support.ParameterDeclarations;

/**
 * Amounts that every money endpoint must refuse, with the issue the API reports for each.
 * Shared by the deposit and transfer tests so both endpoints face the same list.
 */
final class InvalidAmounts implements ArgumentsProvider {

    private static final String FORMAT = "must be a decimal with at most 2 decimal places";
    private static final String TEXT = "must be a string such as \"10.50\"";

    @Override
    public Stream<? extends Arguments> provideArguments(ParameterDeclarations parameters, ExtensionContext context) {
        return Stream.of(
                argumentSet("zero", "0", "must be greater than zero"),
                argumentSet("zero with decimals", "0.00", "must be greater than zero"),
                argumentSet("negative", "-10.00", FORMAT),
                argumentSet("three decimals", "10.005", FORMAT),
                argumentSet("comma as decimal separator", "10,50", FORMAT),
                argumentSet("thousands separator", "1,000.00", FORMAT),
                argumentSet("scientific notation", "1e3", FORMAT),
                argumentSet("leading zero", "010.00", FORMAT),
                argumentSet("no digits before the point", ".50", FORMAT),
                argumentSet("no digits after the point", "10.", FORMAT),
                argumentSet("leading space", " 10.00", FORMAT),
                argumentSet("currency symbol", "$10.00", FORMAT),
                argumentSet("words", "ten", FORMAT),
                argumentSet("empty string", "", FORMAT),
                argumentSet("fourteen digits", "10000000000000", FORMAT),
                argumentSet("JSON number instead of text", 10.5, TEXT),
                argumentSet("JSON integer instead of text", 10, TEXT),
                argumentSet("boolean", true, TEXT),
                argumentSet("null", null, "is required"));
    }
}
