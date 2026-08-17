package io.casehub.flow.graphql;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ComposedSchemaIT {

    @Test
    void schemaContainsAllDomainTypes() {
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

        List<String> typeNames = response.jsonPath().getList("data.__schema.types.name", String.class);

        assertThat(typeNames).as("Engine types")
                .contains("CaseInstance", "CasePage", "CaseControl");
        assertThat(typeNames).as("Qhorus types")
                .contains("Channel", "ChannelPage", "QhorusMessage", "CommitmentPage");
        assertThat(typeNames).as("Platform types")
                .contains("PlatformInfo", "PageInfo", "PageInput");
    }

    @Test
    void schemaContainsAllDomainQueries() {
        var response = given()
                .contentType("application/json")
                .body("""
                        {"query": "{ __schema { queryType { fields { name } } } }"}
                        """)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .extract().response();

        List<String> queryNames = response.jsonPath().getList(
                "data.__schema.queryType.fields.name", String.class);

        assertThat(queryNames).as("Engine queries")
                .contains("cases", "caseById", "caseDefinitions");
        assertThat(queryNames).as("Qhorus queries")
                .contains("channels", "channel", "channelMessages", "commitments");
        assertThat(queryNames).as("Platform queries")
                .contains("platformInfo");
    }

    @Test
    void schemaContainsAllDomainMutations() {
        var response = given()
                .contentType("application/json")
                .body("""
                        {"query": "{ __schema { mutationType { fields { name } } } }"}
                        """)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .extract().response();

        List<String> mutationNames = response.jsonPath().getList(
                "data.__schema.mutationType.fields.name", String.class);

        assertThat(mutationNames).as("Engine mutations")
                .contains("startCase", "cancelCase");
        assertThat(mutationNames).as("Qhorus mutations")
                .contains("createChannel", "deleteChannel", "pauseChannel",
                        "resumeChannel", "dispatchMessage");
    }
}
