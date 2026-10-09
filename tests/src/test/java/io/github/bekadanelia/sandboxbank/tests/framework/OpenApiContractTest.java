package io.github.bekadanelia.sandboxbank.tests.framework;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.bekadanelia.sandboxbank.api.contract.ContractViolation;
import io.github.bekadanelia.sandboxbank.api.contract.OpenApiContract;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests for the contract checker itself. A checker that silently accepts everything is worse
 * than none, so it has to prove that it can fail.
 */
@Tag("framework")
@Epic("Test framework")
@Feature("Contract checker")
class OpenApiContractTest {

    private static final OpenApiContract CONTRACT = OpenApiContract.parse("""
            openapi: 3.1.0
            info: {title: Sample, version: 1.0.0}
            paths:
              /pets/{petId}:
                get:
                  responses:
                    '200':
                      description: A pet
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Pet'
                    '404':
                      $ref: '#/components/responses/NotFound'
                delete:
                  responses:
                    '204':
                      description: Gone
            components:
              responses:
                NotFound:
                  description: No such pet
                  content:
                    application/json:
                      schema:
                        $ref: '#/components/schemas/Problem'
              schemas:
                Pet:
                  type: object
                  additionalProperties: false
                  required: [id, name, price]
                  properties:
                    id: {type: string, format: uuid}
                    name: {type: string}
                    price: {$ref: '#/components/schemas/Money'}
                    owner: {type: [string, 'null']}
                Money:
                  type: string
                  pattern: '^\\d+\\.\\d{2}$'
                Problem:
                  type: object
                  required: [code]
                  properties:
                    code: {type: string}
            """);

    private static final String PET = """
            {"id": "3f0c2b1e-8d4a-4c59-9d2e-5a1b7c6d8e9f", "name": "Rex", "price": "12.50", "owner": null}""";

    @Test
    void a_response_that_matches_its_schema_passes() {
        assertThatCode(() -> CONTRACT.check("GET", "/pets/42", 200, PET)).doesNotThrowAnyException();
    }

    @Test
    void a_response_defined_by_reference_is_resolved_and_checked() {
        assertThatCode(() -> CONTRACT.check("GET", "/pets/42", 404, "{\"code\": \"NO_PET\"}"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> CONTRACT.check("GET", "/pets/42", 404, "{}"))
                .isInstanceOf(ContractViolation.class);
    }

    @Test
    void a_status_code_the_contract_does_not_list_is_a_violation() {
        assertThatThrownBy(() -> CONTRACT.check("GET", "/pets/42", 500, "{}"))
                .isInstanceOf(ContractViolation.class)
                .hasMessageContaining("GET /pets/{petId} answered 500")
                .hasMessageContaining("[200, 404]");
    }

    @Test
    void a_field_the_schema_does_not_know_is_a_violation() {
        String withExtraField = PET.replace("\"name\"", "\"internalNote\": \"x\", \"name\"");

        assertThatThrownBy(() -> CONTRACT.check("GET", "/pets/42", 200, withExtraField))
                .isInstanceOf(ContractViolation.class);
    }

    @Test
    void a_missing_required_field_is_a_violation() {
        assertThatThrownBy(() -> CONTRACT.check("GET", "/pets/42", 200, PET.replace("\"name\": \"Rex\",", "")))
                .isInstanceOf(ContractViolation.class)
                .hasMessageContaining("name");
    }

    @Test
    void a_value_that_breaks_a_referenced_pattern_is_a_violation() {
        assertThatThrownBy(() -> CONTRACT.check("GET", "/pets/42", 200, PET.replace("12.50", "12.5")))
                .isInstanceOf(ContractViolation.class)
                .hasMessageContaining("/price");
    }

    @Test
    void a_value_that_breaks_a_declared_format_is_a_violation() {
        String notAUuid = PET.replace("3f0c2b1e-8d4a-4c59-9d2e-5a1b7c6d8e9f", "42");

        assertThatThrownBy(() -> CONTRACT.check("GET", "/pets/42", 200, notAUuid))
                .isInstanceOf(ContractViolation.class)
                .hasMessageContaining("/id");
    }

    @Test
    void a_body_that_is_not_json_is_a_violation() {
        assertThatThrownBy(() -> CONTRACT.check("GET", "/pets/42", 200, "<html>Bad gateway</html>"))
                .isInstanceOf(ContractViolation.class);
    }

    @Test
    void a_documented_response_without_a_body_is_not_checked_against_a_schema() {
        assertThatCode(() -> CONTRACT.check("DELETE", "/pets/42", 204, "")).doesNotThrowAnyException();
    }

    @Test
    void an_address_the_contract_does_not_describe_is_left_alone() {
        assertThatCode(() -> CONTRACT.check("GET", "/owners/7", 500, "anything")).doesNotThrowAnyException();
        assertThatCode(() -> CONTRACT.check("GET", "/pets/42/toys", 500, "anything")).doesNotThrowAnyException();
    }
}
