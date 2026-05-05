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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for combined loader behavior.
 *
 * <p>Verifies that all three loader types work together correctly:
 *
 * <ul>
 *   <li>CDI beans extending CaseHub (via DefaultCaseDefinitionRegistry)
 *   <li>Non-CDI CaseHub classes from classpath (via CaseHubClassPathLoader)
 *   <li>YAML definitions from classpath (via YamlCaseDefinitionLoader)
 * </ul>
 *
 * <p>Test data sources:
 *
 * <ul>
 *   <li>CDI beans: 3 definitions from CaseDefinitionResourceTest inner classes
 *   <li>Classpath: 1 definition from ClasspathOnlyCaseHub
 *   <li>YAML: 3 definitions (test-yaml-definition.yaml, valid/minimal.yaml,
 *       valid/complete.yaml)
 * </ul>
 */
@QuarkusTest
class LoaderIntegrationTest {

  @Test
  void allLoaderTypesWorkTogether() {
    // Expected: 3 CDI beans + 1 classpath + 3 YAML = 7 total
    // CDI beans: DocumentApprovalV1, DocumentApprovalV2, InvoiceProcessing
    // Classpath: ClasspathOnlyCaseHub
    // YAML: test-yaml-definition.yaml, valid/minimal.yaml, valid/complete.yaml
    given()
        .when()
        .get("/api/v1/case-definitions")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("totalElements", equalTo(7))
        .body("totalPages", equalTo(1));
  }

  @Test
  void cdiBeanDefinitionsAreLoaded() {
    // Verify CDI beans are loaded (from CaseDefinitionResourceTest)
    // Document Approval v1.0.0
    given()
        .when()
        .get("/api/v1/case-definitions/test-api/Document%20Approval/1.0.0")
        .then()
        .statusCode(200);

    // Document Approval v2.0.0
    given()
        .when()
        .get("/api/v1/case-definitions/test-api/Document%20Approval/2.0.0")
        .then()
        .statusCode(200);

    // Invoice Processing
    given()
        .when()
        .get("/api/v1/case-definitions/test-api/Invoice%20Processing/1.0.0")
        .then()
        .statusCode(200);
  }

  @Test
  void classpathDefinitionsAreLoaded() {
    // Verify classpath definition is loaded (ClasspathOnlyCaseHub without CDI annotation)
    given()
        .when()
        .get("/api/v1/case-definitions/test-classpath/Classpath%20Only%20Case/1.0.0")
        .then()
        .statusCode(200)
        .body("namespace", equalTo("test-classpath"))
        .body("name", equalTo("Classpath Only Case"))
        .body("version", equalTo("1.0.0"));
  }

  @Test
  void yamlDefinitionsAreLoaded() {
    // Verify YAML definitions are loaded
    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/api/v1/case-definitions/test-valid/Minimal%20Test/1.0.0")
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/api/v1/case-definitions/test-valid/Complete%20Test/2.0.0")
        .then()
        .statusCode(200);
  }

  @Test
  void multipleVersionsOfSameDefinitionCoexist() {
    // Document Approval has versions 1.0.0 and 2.0.0
    given()
        .when()
        .get("/api/v1/case-definitions/test-api/Document%20Approval")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("$", hasSize(2))
        .body("[0].version", notNullValue())
        .body("[1].version", notNullValue())
        .body("[0].version", not(equalTo("[1].version")));
  }

  @Test
  void definitionsFromDifferentSourcesHaveDistinctNamespaces() {
    // Expected namespaces: test-api, test-classpath, test-yaml, test-valid
    // Verify each namespace exists by checking at least one definition from each
    given()
        .when()
        .get("/api/v1/case-definitions/test-api/Document%20Approval/1.0.0")
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/api/v1/case-definitions/test-classpath/Classpath%20Only%20Case/1.0.0")
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200);

    given()
        .when()
        .get("/api/v1/case-definitions/test-valid/Minimal%20Test/1.0.0")
        .then()
        .statusCode(200);
  }

  @Test
  void allYamlDefinitionsHaveRequiredFields() {
    // Verify YAML definitions have namespace, name, version
    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200)
        .body("namespace", equalTo("test-yaml"))
        .body("name", equalTo("YAML Test Case"))
        .body("version", equalTo("1.0.0"));

    given()
        .when()
        .get("/api/v1/case-definitions/test-valid/Minimal%20Test/1.0.0")
        .then()
        .statusCode(200)
        .body("namespace", equalTo("test-valid"))
        .body("name", equalTo("Minimal Test"))
        .body("version", equalTo("1.0.0"));
  }

  @Test
  void yamlDefinitionsHaveCapabilitiesAndWorkers() {
    // All YAML definitions should have at least one capability and one worker
    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200)
        .body("capabilities", hasSize(1))
        .body("workers", hasSize(1));

    given()
        .when()
        .get("/api/v1/case-definitions/test-valid/Minimal%20Test/1.0.0")
        .then()
        .statusCode(200)
        .body("capabilities", hasSize(1))
        .body("workers", hasSize(1));

    given()
        .when()
        .get("/api/v1/case-definitions/test-valid/Complete%20Test/2.0.0")
        .then()
        .statusCode(200)
        .body("capabilities", hasSize(2))
        .body("workers", hasSize(2));
  }
}
