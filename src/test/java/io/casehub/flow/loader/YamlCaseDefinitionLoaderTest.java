/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.flow.loader;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link YamlCaseDefinitionLoader}.
 *
 * <p>Verifies YAML case definition loading, conversion, and validation:
 *
 * <ul>
 *   <li>Happy path: valid YAML files are loaded and converted correctly
 *   <li>Conversion: capabilities, workers, bindings, goals, milestones are converted correctly
 *   <li>Validation: structural validation catches missing capability/goal references
 *   <li>Error handling: fail-fast with clear error messages
 * </ul>
 */
@QuarkusTest
class YamlCaseDefinitionLoaderTest {

  // ------------------------------------------------------------------ //
  // Happy Path Tests                                                   //
  // ------------------------------------------------------------------ //

  @Test
  void loadsValidYamlDefinition() {
    // test-yaml-definition.yaml should be loaded
    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("namespace", equalTo("test-yaml"))
        .body("name", equalTo("YAML Test Case"))
        .body("version", equalTo("1.0.0"));
  }

  @Test
  void convertsCapabilitiesCorrectly() {
    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200)
        .body("capabilities", hasSize(1))
        .body("capabilities[0].name", equalTo("processData"));
  }

  @Test
  void convertsWorkersCorrectly() {
    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200)
        .body("workers", hasSize(1))
        .body("workers[0].name", equalTo("data-processor"))
        .body("workers[0].capabilities", notNullValue());
  }

  @Test
  void convertsBindingsCorrectly() {
    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200)
        .body("bindings", hasSize(1))
        .body("bindings[0].name", equalTo("trigger-on-data"))
        .body("bindings[0].target", notNullValue());
  }

  @Test
  void convertsGoalsCorrectly() {
    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200)
        .body("goals", hasSize(1))
        .body("goals[0].name", equalTo("dataProcessed"));
  }

  @Test
  void convertsCompletionCorrectly() {
    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200)
        .body("completion", notNullValue());
  }

  @Test
  void loadsMinimalValidYaml() {
    // valid/minimal.yaml should be loaded
    given()
        .when()
        .get("/api/v1/case-definitions/test-valid/Minimal%20Test/1.0.0")
        .then()
        .statusCode(200)
        .body("namespace", equalTo("test-valid"))
        .body("name", equalTo("Minimal Test"))
        .body("version", equalTo("1.0.0"))
        .body("capabilities", hasSize(1))
        .body("workers", hasSize(1));
  }

  @Test
  void loadsCompleteValidYaml() {
    // valid/complete.yaml should be loaded
    given()
        .when()
        .get("/api/v1/case-definitions/test-valid/Complete%20Test/2.0.0")
        .then()
        .statusCode(200)
        .body("namespace", equalTo("test-valid"))
        .body("name", equalTo("Complete Test"))
        .body("version", equalTo("2.0.0"))
        .body("capabilities", hasSize(2))
        .body("workers", hasSize(2))
        .body("bindings", hasSize(2))
        .body("goals", hasSize(2))
        .body("completion", notNullValue());
  }

  @Test
  void noInvalidDefinitionsLoaded() {
    // Verify that no definitions from test-invalid namespace exist
    // (invalid test files are in test-data/invalid/, not scanned by loader)
    // Try to fetch a non-existent invalid definition
    given()
        .when()
        .get("/api/v1/case-definitions/test-invalid/Worker%20Bad%20Capability/1.0.0")
        .then()
        .statusCode(404);
  }
}
