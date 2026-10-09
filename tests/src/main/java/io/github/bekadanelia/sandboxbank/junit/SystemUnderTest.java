package io.github.bekadanelia.sandboxbank.junit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bekadanelia.sandboxbank.config.TestConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * Checks once per run that the bank is up before any test touches it.
 *
 * <p>When it is not, every test class fails at once with one clear sentence instead of a
 * hundred connection errors. The classes fail, they are not skipped, so a broken environment
 * can never look like a passing build.
 */
public final class SystemUnderTest implements BeforeAllCallback {

    private static final Namespace NAMESPACE = Namespace.create(SystemUnderTest.class);

    /** What /health reported, or the reason why it could not be reached. */
    public record Health(String version, List<String> faults, String problem) {

        public boolean up() {
            return problem == null;
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        Health health = context.getRoot().getStore(NAMESPACE).computeIfAbsent(Health.class, key -> {
            Health probed = probe();
            if (probed.up()) {
                RunEnvironment.record(probed);
            }
            return probed;
        }, Health.class);
        if (!health.up()) {
            throw new IllegalStateException(health.problem());
        }
    }

    static Health probe() {
        String url = TestConfig.baseUrl() + "/health";
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = new ObjectMapper().readTree(response.body());
            if (response.statusCode() != 200 || !"UP".equals(body.path("status").asText())) {
                return new Health(null, List.of(), "Sandbox Bank is not healthy: %s answered %d %s"
                        .formatted(url, response.statusCode(), response.body()));
            }
            List<String> faults = new ArrayList<>();
            body.path("faults").forEach(fault -> faults.add(fault.asText()));
            return new Health(body.path("version").asText(), faults, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Health(null, List.of(), "Interrupted while checking " + url);
        } catch (Exception e) {
            return new Health(null, List.of(), ("Sandbox Bank is not reachable at %s (%s). "
                    + "Start it with \"docker compose up -d --wait\" or point base.url at a running instance.")
                    .formatted(url, e));
        }
    }
}
