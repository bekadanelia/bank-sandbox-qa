package io.github.bekadanelia.sandboxbank.api.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import dev.harrel.jsonschema.FormatEvaluatorFactory;
import dev.harrel.jsonschema.JsonSchemaException;
import dev.harrel.jsonschema.Validator;
import dev.harrel.jsonschema.ValidatorFactory;
import dev.harrel.jsonschema.providers.JacksonNode;
import io.github.bekadanelia.sandboxbank.config.TestConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The API contract, read from the openapi.yaml that the running system serves.
 *
 * <p>OpenAPI 3.1 schemas are JSON Schema 2020-12, so they are handed to a JSON Schema validator
 * as they are. The only translation is the location of the shared schemas.
 */
public final class OpenApiContract {

    private static final String COMPONENTS_ID = "https://contract.sandbox.bank/components";
    private static final String RESPONSE_ID = "https://contract.sandbox.bank/responses/";
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Validator validator = new ValidatorFactory()
            .withEvaluatorFactory(new FormatEvaluatorFactory())
            .withJsonNodeFactory(new JacksonNode.Factory())
            .createValidator();
    private final List<Operation> operations = new ArrayList<>();
    private final Set<String> exercised = new ConcurrentSkipListSet<>();

    /** One documented operation. A status maps to an empty schema location when it has no JSON body. */
    private record Operation(String method, String path, Pattern pathPattern, boolean secured,
                             Map<Integer, Optional<URI>> responses) {

        String name() {
            return method + " " + path;
        }
    }

    private static final class Holder {
        static final OpenApiContract PUBLISHED = parse(download(TestConfig.baseUrl() + "/openapi.yaml"));
    }

    /** The contract of the system under test. Downloaded once per run. */
    public static OpenApiContract published() {
        return Holder.PUBLISHED;
    }

    public static OpenApiContract parse(String yaml) {
        try {
            return new OpenApiContract(new YAMLMapper().readTree(yaml));
        } catch (IOException e) {
            throw new UncheckedIOException("openapi.yaml cannot be parsed", e);
        }
    }

    private OpenApiContract(JsonNode document) {
        ObjectNode components = JSON.createObjectNode();
        components.put("$id", COMPONENTS_ID);
        components.set("$defs", document.at("/components/schemas"));
        validator.registerSchema(URI.create(COMPONENTS_ID), relocate(components));

        for (Map.Entry<String, JsonNode> pathItem : document.path("paths").properties()) {
            for (Map.Entry<String, JsonNode> member : pathItem.getValue().properties()) {
                if (HTTP_METHODS.contains(member.getKey())) {
                    register(document, member.getKey(), pathItem.getKey(), member.getValue());
                }
            }
        }
        // A literal path such as /customers/me must win over a template that would also match it.
        operations.sort(Comparator.comparingInt(operation -> operation.path().split("\\{").length));
    }

    private void register(JsonNode document, String method, String path, JsonNode operation) {
        Map<Integer, Optional<URI>> responses = new TreeMap<>();
        for (Map.Entry<String, JsonNode> entry : operation.path("responses").properties()) {
            Integer status = Integer.valueOf(entry.getKey());
            JsonNode response = entry.getValue();
            if (response.has("$ref")) {
                response = document.at(response.get("$ref").asText().substring(1));
            }
            JsonNode schema = response.at("/content/application~1json/schema");
            if (schema.isMissingNode()) {
                responses.put(status, Optional.empty());
                continue;
            }
            URI id = URI.create(RESPONSE_ID + method + path.replaceAll("[{}]", "") + "/" + status);
            ObjectNode identified = schema.deepCopy();
            identified.put("$id", id.toString());
            validator.registerSchema(id, relocate(identified));
            responses.put(status, Optional.of(id));
        }
        Pattern pathPattern = Pattern.compile(path.replaceAll("\\{[^/}]+}", "[^/]+"));
        // An operation is public only when it overrides the document wide security with an empty list.
        boolean secured = !(operation.has("security") && operation.get("security").isEmpty());
        operations.add(new Operation(method.toUpperCase(Locale.ROOT), path, pathPattern, secured, responses));
    }

    /** Points references at the place where the shared schemas were registered. */
    private static String relocate(JsonNode schema) {
        return schema.toString().replace("\"#/components/schemas/", "\"" + COMPONENTS_ID + "#/$defs/");
    }

    /**
     * Compares a real response with the contract.
     *
     * @param path the request path below the API root, for example {@code /accounts/123}
     * @throws ContractViolation when the status code is not documented or the body does not match its schema
     */
    public void check(String method, String path, int status, String body) {
        Optional<Operation> match = operations.stream()
                .filter(operation -> operation.method().equalsIgnoreCase(method))
                .filter(operation -> operation.pathPattern().matcher(path).matches())
                .findFirst();
        if (match.isEmpty()) {
            return; // nothing is documented at this address, so there is nothing to compare with
        }
        Operation operation = match.get();
        Optional<URI> schema = operation.responses().get(status);
        if (schema == null) {
            throw new ContractViolation("%s answered %d, which openapi.yaml does not document. Documented: %s"
                    .formatted(operation.name(), status, operation.responses().keySet()));
        }
        exercised.add(operation.name() + " " + status);
        if (schema.isEmpty()) {
            return;
        }
        List<String> problems = problemsOf(schema.get(), body);
        if (!problems.isEmpty()) {
            throw new ContractViolation("%s answered %d with a body that breaks the contract:%n  %s%nBody: %s"
                    .formatted(operation.name(), status, String.join("\n  ", problems), body));
        }
    }

    private List<String> problemsOf(URI schema, String body) {
        try {
            return validator.validate(schema, body).getErrors().stream()
                    .map(error -> "%s %s".formatted(
                            error.getInstanceLocation().isEmpty() ? "(root)" : error.getInstanceLocation(),
                            error.getError()))
                    .distinct()
                    .toList();
        } catch (JsonSchemaException | IllegalArgumentException e) {
            return List.of("the body is not JSON: " + e.getMessage());
        }
    }

    /** A documented operation, for example {@code POST} on {@code /accounts/{accountId}/freeze}. */
    public record Endpoint(String method, String path) {

        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    /** The operations that the contract says need a bearer token. */
    public List<Endpoint> securedEndpoints() {
        return operations.stream()
                .filter(Operation::secured)
                .map(operation -> new Endpoint(operation.method(), operation.path()))
                .toList();
    }

    /** Every documented response, written like {@code POST /transfers 201}. */
    public Set<String> documentedResponses() {
        return operations.stream()
                .flatMap(operation -> operation.responses().keySet().stream()
                        .map(status -> operation.name() + " " + status))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** The documented responses that at least one request of this run has produced. */
    public Set<String> exercisedResponses() {
        return new TreeSet<>(exercised);
    }

    private static String download(String url) {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(url + " answered " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot download the API contract from " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while downloading the API contract", e);
        }
    }
}
