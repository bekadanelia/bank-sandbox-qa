package io.github.bekadanelia.sandboxbank.tests.framework;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bekadanelia.sandboxbank.config.TestConfig;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Keeps scripts/seeded-defects.csv true.
 *
 * <p>The manifest names tests by class and method. Renaming a test would silently break the
 * seeded defect check, and that would only show in the nightly run. This test shows it at once.
 */
@Tag("framework")
@Epic("Test framework")
@Feature("Seeded defects")
class SeededDefectsManifestTest {

    private static final String TESTS_PACKAGE = "io.github.bekadanelia.sandboxbank.tests.";
    private static final List<String> SUITE_PACKAGES = List.of("api", "db", "ui", "e2e");

    private record Entry(String defect, String suites, String rule, List<String> tests) {
    }

    private static List<Entry> manifest;

    @BeforeAll
    static void readManifest() throws IOException {
        manifest = Files.readAllLines(TestConfig.seededDefectsManifest()).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.split(";"))
                .map(columns -> new Entry(columns[0], columns[1], columns[2], Arrays.asList(columns[3].split(","))))
                .toList();
    }

    @Test
    void every_defect_the_bank_can_switch_on_is_listed_exactly_once() throws IOException {
        Matcher names = Pattern.compile("(?m)^  ([A-Z][A-Z_]+): '")
                .matcher(Files.readString(TestConfig.seededDefectsSource()));

        assertThat(manifest).extracting(Entry::defect)
                .containsExactlyInAnyOrderElementsOf(names.results().map(match -> match.group(1)).toList());
    }

    @Test
    void every_listed_test_exists() {
        assertThat(manifest).allSatisfy(entry -> assertThat(entry.tests()).allSatisfy(test ->
                assertThat(exists(test)).as("test %s named for %s", test, entry.defect()).isTrue()));
    }

    @Test
    void every_listed_rule_is_a_documented_requirement() throws IOException {
        String requirements = Files.readString(TestConfig.requirementsFile());

        assertThat(manifest).allSatisfy(entry ->
                assertThat(requirements).as("rule of %s", entry.defect()).contains(">" + entry.rule() + " |"));
    }

    /** True when some suite package holds the class and the class declares the method. */
    private static boolean exists(String classAndMethod) {
        int dot = classAndMethod.lastIndexOf('.');
        String className = classAndMethod.substring(0, dot);
        String methodName = classAndMethod.substring(dot + 1);
        return SUITE_PACKAGES.stream().anyMatch(suite -> {
            try {
                Class<?> testClass = Class.forName(TESTS_PACKAGE + suite + "." + className);
                return Arrays.stream(testClass.getDeclaredMethods())
                        .anyMatch(method -> method.getName().equals(methodName));
            } catch (ClassNotFoundException e) {
                return false;
            }
        });
    }
}
