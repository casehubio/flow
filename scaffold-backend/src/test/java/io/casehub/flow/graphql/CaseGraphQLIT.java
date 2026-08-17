package io.casehub.flow.graphql;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class CaseGraphQLIT {

    @Test
    void startCaseMutationCreatesCaseInstance() {
        var response = given()
                .contentType("application/json")
                .body("""
                        {"query": "mutation { startCase(input: { namespace: \\"test-valid\\", name: \\"Minimal Test\\", version: \\"1.0.0\\" }) { caseId status namespace name version } }"}
                        """)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.startCase.caseId", notNullValue())
                .body("data.startCase.status", equalTo("RUNNING"))
                .body("data.startCase.namespace", equalTo("test-valid"))
                .body("data.startCase.name", equalTo("Minimal Test"))
                .extract().response();

        String caseId = response.jsonPath().getString("data.startCase.caseId");
        assertThat(caseId).isNotNull();
    }

    @Test
    void caseByIdQueryReturnsCaseAfterStart() {
        // Start a case first
        var startResponse = given()
                .contentType("application/json")
                .body("""
                        {"query": "mutation { startCase(input: { namespace: \\"test-valid\\", name: \\"Minimal Test\\", version: \\"1.0.0\\" }) { caseId } }"}
                        """)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .extract().response();

        String caseId = startResponse.jsonPath().getString("data.startCase.caseId");

        // Query it back
        given()
                .contentType("application/json")
                .body(String.format("""
                        {"query": "{ caseById(caseId: \\"%s\\") { caseId status namespace name version actorId } }"}
                        """, caseId))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.caseById.caseId", equalTo(caseId))
                .body("data.caseById.status", equalTo("RUNNING"))
                .body("data.caseById.namespace", equalTo("test-valid"));
    }

    @Test
    void caseDefinitionsQueryReturnsLoadedDefinitions() {
        given()
                .contentType("application/json")
                .body("""
                        {"query": "{ caseDefinitions(page: { offset: 0, limit: 10 }) { items { namespace name version } pageInfo { totalCount hasNext } } }"}
                        """)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.caseDefinitions.pageInfo.totalCount", notNullValue())
                .body("data.caseDefinitions.items", notNullValue());
    }

    @Test
    void composedSchemaContainsEngineTypes() {
        var response = given()
                .contentType("application/json")
                .body("""
                        {"query": "{ __schema { types { name } } }"}
                        """)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .extract().response();

        var typeNames = response.jsonPath().getList("data.__schema.types.name", String.class);
        assertThat(typeNames)
                .contains("CaseInstance", "CasePage", "CaseControl", "SignalResult",
                        "CaseDefinitionResponse", "EventLogEntry", "PlatformInfo");
    }

    @Test
    void cancelCaseMutationTransitionsToTerminal() {
        // Start a case
        var startResponse = given()
                .contentType("application/json")
                .body("""
                        {"query": "mutation { startCase(input: { namespace: \\"test-valid\\", name: \\"Minimal Test\\", version: \\"1.0.0\\" }) { caseId } }"}
                        """)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .extract().response();

        String caseId = startResponse.jsonPath().getString("data.startCase.caseId");

        // Cancel it
        given()
                .contentType("application/json")
                .body(String.format("""
                        {"query": "mutation { cancelCase(caseId: \\"%s\\") { caseId status } }"}
                        """, caseId))
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.cancelCase.caseId", equalTo(caseId))
                .body("data.cancelCase.status", notNullValue());
    }
}
