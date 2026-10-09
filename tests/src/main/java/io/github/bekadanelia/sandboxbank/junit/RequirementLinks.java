package io.github.bekadanelia.sandboxbank.junit;

import io.github.bekadanelia.sandboxbank.config.TestConfig;
import io.qameta.allure.Allure;
import java.lang.reflect.AnnotatedElement;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

/** Turns {@link Requirement} ids into report links that open the rule in docs/requirements.md. */
public final class RequirementLinks implements BeforeEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        for (String id : requirementsOf(context)) {
            Allure.link(id, "requirement", TestConfig.requirementsUrl() + "#" + id.toLowerCase(Locale.ROOT));
        }
    }

    /** Ids on the test method first, then on its class and the classes around it. */
    public static Set<String> requirementsOf(ExtensionContext context) {
        Set<String> ids = new LinkedHashSet<>();
        context.getTestMethod().ifPresent(method -> collect(method, ids));
        Optional<Class<?>> testClass = context.getTestClass();
        while (testClass.isPresent()) {
            collect(testClass.get(), ids);
            testClass = Optional.ofNullable(testClass.get().getEnclosingClass());
        }
        return ids;
    }

    private static void collect(AnnotatedElement element, Set<String> ids) {
        AnnotationSupport.findAnnotation(element, Requirement.class)
                .ifPresent(requirement -> ids.addAll(Set.of(requirement.value())));
    }
}
