package io.github.bekadanelia.sandboxbank.junit;

import io.github.bekadanelia.sandboxbank.config.TestConfig;
import io.qameta.allure.util.PropertiesUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Writes what was tested and where into the report, so a result can be traced to a build. */
final class RunEnvironment {

    private RunEnvironment() {
    }

    static void record(SystemUnderTest.Health health) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("Sandbox Bank version", health.version());
        facts.put("Seeded faults", health.faults().isEmpty() ? "none" : String.join(", ", health.faults()));
        facts.put("Base URL", TestConfig.baseUrl());
        facts.put("Browser", TestConfig.browser() + (TestConfig.headless() ? " (headless)" : ""));
        facts.put("Java", System.getProperty("java.version"));
        facts.put("OS", System.getProperty("os.name") + " " + System.getProperty("os.arch"));

        Path directory = Path.of(PropertiesUtils.loadAllureProperties()
                .getProperty("allure.results.directory", "allure-results"));
        String content = facts.entrySet().stream()
                .map(fact -> fact.getKey().replace(" ", "\\ ") + "=" + fact.getValue().replace("\\", "\\\\"))
                .collect(Collectors.joining("\n", "", "\n"));
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("environment.properties"), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
