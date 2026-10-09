package io.github.bekadanelia.sandboxbank.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Names the business rules a test verifies, by their ids in docs/requirements.md.
 *
 * <p>The ids become links in the report, and the traceability check uses them to prove that
 * every rule has at least one test.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RequirementLinks.class)
public @interface Requirement {

    String[] value();
}
