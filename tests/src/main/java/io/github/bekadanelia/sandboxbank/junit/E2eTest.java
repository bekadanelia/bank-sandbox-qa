package io.github.bekadanelia.sandboxbank.junit;

import io.github.bekadanelia.sandboxbank.ui.BrowserExtension;
import io.qameta.allure.LabelAnnotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/** Journeys that cross the web client, the API and the database in one test. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("e2e")
@LabelAnnotation(name = "layer", value = "End to end")
@ExtendWith({SystemUnderTest.class, BrowserExtension.class})
public @interface E2eTest {
}
