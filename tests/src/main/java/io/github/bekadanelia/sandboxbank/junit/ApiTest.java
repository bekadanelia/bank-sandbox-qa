package io.github.bekadanelia.sandboxbank.junit;

import io.qameta.allure.LabelAnnotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/** Tests that talk to the bank over HTTP only. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("api")
@LabelAnnotation(name = "layer", value = "API")
@ExtendWith({SystemUnderTest.class})
public @interface ApiTest {
}
