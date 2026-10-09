package io.github.bekadanelia.sandboxbank.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * The few tests that decide whether a build is worth testing further.
 * They run first in the pipeline and finish in seconds.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("smoke")
public @interface Smoke {
}
