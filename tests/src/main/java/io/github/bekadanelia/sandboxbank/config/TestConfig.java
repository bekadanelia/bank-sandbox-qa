package io.github.bekadanelia.sandboxbank.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/**
 * Settings of a test run.
 *
 * <p>Each key is resolved in this order: JVM system property, environment variable
 * ({@code base.url} becomes {@code BASE_URL}), then {@code config.properties}.
 */
public final class TestConfig {

    private static final Properties DEFAULTS = loadDefaults();

    private TestConfig() {
    }

    public record Credentials(String user, String password) {
    }

    public static String baseUrl() {
        return get("base.url").replaceAll("/+$", "");
    }

    public static String apiUrl() {
        return baseUrl() + "/api/v1";
    }

    public static String dbUrl() {
        return get("db.url");
    }

    public static Credentials dbReader() {
        return new Credentials(get("db.reader.user"), get("db.reader.password"));
    }

    public static Credentials dbOwner() {
        return new Credentials(get("db.owner.user"), get("db.owner.password"));
    }

    public static String jwtSecret() {
        return get("jwt.secret");
    }

    public static String browser() {
        return get("browser").toLowerCase(Locale.ROOT);
    }

    public static boolean headless() {
        return Boolean.parseBoolean(get("headless"));
    }

    public static double slowMotionMillis() {
        return Double.parseDouble(get("slow.motion.ms"));
    }

    public static Optional<Path> browserExecutable() {
        return Optional.of(get("browser.executable")).filter(path -> !path.isBlank()).map(Path::of);
    }

    public static boolean contractChecks() {
        return Boolean.parseBoolean(get("contract.checks"));
    }

    public static String requirementsUrl() {
        return get("requirements.url");
    }

    public static Path requirementsFile() {
        return Path.of(get("requirements.file"));
    }

    public static Path seededDefectsManifest() {
        return Path.of(get("seeded.defects.manifest"));
    }

    public static Path seededDefectsSource() {
        return Path.of(get("seeded.defects.source"));
    }

    private static String get(String key) {
        String fromJvm = System.getProperty(key);
        if (fromJvm != null) {
            return fromJvm.trim();
        }
        String fromEnvironment = System.getenv(key.toUpperCase(Locale.ROOT).replace('.', '_'));
        if (fromEnvironment != null) {
            return fromEnvironment.trim();
        }
        String fromFile = DEFAULTS.getProperty(key);
        if (fromFile == null) {
            throw new IllegalStateException("No value for setting '" + key + "'");
        }
        return fromFile.trim();
    }

    private static Properties loadDefaults() {
        Properties properties = new Properties();
        try (InputStream file = TestConfig.class.getResourceAsStream("/config.properties")) {
            if (file == null) {
                throw new IllegalStateException("config.properties is missing from the classpath");
            }
            properties.load(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}
