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
package io.casehub.flow.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CaseInstanceResourceTest {

  @Test
  void startCase_cdiBeanDefinition_returns200WithCorrectStatus() {
    Map<String, Object> request =
        Map.of(
            "definition",
            Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
            "context",
            Map.of("documentId", "DOC-123", "submittedBy", "alice@example.com"));

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/v1/cases")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("caseId", notNullValue())
        .body("status", notNullValue())
        .body("namespace", equalTo("test-api"))
        .body("name", equalTo("Document Approval"))
        .body("version", equalTo("1.0.0"))
        .body("createdAt", notNullValue())
        .body("updatedAt", notNullValue());
  }

  @Test
  void startCase_yamlDefinition_returns200() {
    Map<String, Object> request =
        Map.of(
            "definition",
            Map.of("namespace", "test-yaml", "name", "YAML Test Case", "version", "1.0.0"),
            "context",
            Map.of("data", "test-value"));

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/v1/cases")
        .then()
        .statusCode(200)
        .body("caseId", notNullValue())
        .body("namespace", equalTo("test-yaml"))
        .body("name", equalTo("YAML Test Case"));
  }

  @Test
  void startCase_definitionNotFound_returns404WithProblemDetail() {
    Map<String, Object> request =
        Map.of(
            "definition",
            Map.of("namespace", "non-existent", "name", "Unknown Case", "version", "1.0.0"),
            "context",
            Map.of());

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/v1/cases")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Case definition not found"))
        .body("status", equalTo(404))
        .body("detail", notNullValue());
  }

  @Test
  void startCase_invalidRequest_returns400() {
    Map<String, Object> request = Map.of("context", Map.of("key", "value"));

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/v1/cases")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid request"))
        .body("status", equalTo(400));
  }
}
