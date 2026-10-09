package io.github.bekadanelia.sandboxbank.api.contract;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.net.URI;

/**
 * Checks every response of every test against openapi.yaml.
 *
 * <p>Because it sits on the shared request specification, a functional test that was written to
 * check a balance also fails when the response grows an undocumented field or status code.
 */
public final class ContractFilter implements Filter {

    private static final String API_PREFIX = "/api/v1";

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec,
                           FilterContext context) {
        Response response = context.next(request, responseSpec);
        String path = URI.create(request.getURI()).getRawPath();
        if (path.startsWith(API_PREFIX)) {
            OpenApiContract.published().check(
                    request.getMethod(),
                    path.substring(API_PREFIX.length()),
                    response.statusCode(),
                    response.asString());
        }
        return response;
    }
}
