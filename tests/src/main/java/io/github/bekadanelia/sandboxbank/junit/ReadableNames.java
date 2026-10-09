package io.github.bekadanelia.sandboxbank.junit;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGenerator;

/**
 * Shows {@code a_transfer_moves_exactly_the_amount} as "A transfer moves exactly the amount"
 * in the console and in every report.
 *
 * <p>The generator that ships with JUnit also appends the parameter types of the method, which
 * is noise for a reader of a report. This one leaves them out.
 */
public final class ReadableNames extends DisplayNameGenerator.Standard {

    @Override
    public String generateDisplayNameForNestedClass(List<Class<?>> enclosingInstanceTypes, Class<?> nestedClass) {
        return sentence(nestedClass.getSimpleName());
    }

    @Override
    public String generateDisplayNameForMethod(List<Class<?>> enclosingInstanceTypes, Class<?> testClass,
                                               Method testMethod) {
        return sentence(testMethod.getName());
    }

    private static String sentence(String name) {
        String words = name.replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
