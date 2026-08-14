package io.casehub.flow.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.*;

@QuarkusTest
class ModuleRegistryResourceTest {

    @Test
    void returnsAvailableModules() {
        RestAssured.given()
            .when().get("/api/modules")
            .then()
            .statusCode(200)
            .body("modules", hasItem("engine"))
            .body("modules", hasItem("work"));
    }
}
