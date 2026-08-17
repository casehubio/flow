package io.casehub.flow.graphql;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class GraphQLEndpointIT {

    @Test
    void graphqlEndpointAcceptsQueries() {
        given()
                .contentType("application/json")
                .body("""
                        {"query": "{ platformInfo { name version } }"}
                        """)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.platformInfo.name", equalTo("CaseHub"))
                .body("data.platformInfo.version", notNullValue());
    }

    @Test
    void graphqlIntrospectionReturnsSchema() {
        given()
                .contentType("application/json")
                .body("""
                        {"query": "{ __schema { queryType { name } } }"}
                        """)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body("data.__schema.queryType.name", equalTo("Query"));
    }

    @Test
    void graphqlSchemaContainsSharedTypes() {
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
        assertThat(typeNames).contains("PlatformInfo");
    }

    @Test
    void graphqlUiIsAvailable() {
        given()
                .when()
                .get("/q/graphql-ui")
                .then()
                .statusCode(200)
                .body(containsString("GraphiQL"));
    }
}
