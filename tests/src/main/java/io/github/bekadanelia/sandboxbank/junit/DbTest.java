package io.github.bekadanelia.sandboxbank.junit;

import io.qameta.allure.LabelAnnotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/** Tests that inspect or challenge the database directly. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("db")
@LabelAnnotation(name = "layer", value = "Database")
@ExtendWith({SystemUnderTest.class})
public @interface DbTest {
}
