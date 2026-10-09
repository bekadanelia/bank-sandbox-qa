package io.github.bekadanelia.sandboxbank.junit;

import io.github.bekadanelia.sandboxbank.ui.BrowserExtension;
import io.qameta.allure.LabelAnnotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/** Tests that drive the web client in a real browser. A {@code Page} parameter is injected. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("ui")
@LabelAnnotation(name = "layer", value = "UI")
@ExtendWith({SystemUnderTest.class, BrowserExtension.class})
public @interface UiTest {
}
