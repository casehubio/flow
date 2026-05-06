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
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.UUID;
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

  @Test
  void getCaseInstance_returns200() {
    // Start a case first
    Map<String, Object> startRequest =
        Map.of(
            "definition",
            Map.of("namespace", "test-api", "name", "Invoice Processing", "version", "1.0.0"),
            "context",
            Map.of("invoiceId", "INV-456"));

    String caseId =
        given()
            .contentType(ContentType.JSON)
            .body(startRequest)
            .when()
            .post("/api/v1/cases")
            .then()
            .statusCode(200)
            .extract()
            .path("caseId");

    // Get the case instance
    given()
        .when()
        .get("/api/v1/cases/{caseId}", caseId)
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("caseId", equalTo(caseId))
        .body("namespace", equalTo("test-api"))
        .body("name", equalTo("Invoice Processing"))
        .body("version", equalTo("1.0.0"))
        .body("status", notNullValue());
  }

  @Test
  void getCaseInstance_notFound_returns404() {
    UUID nonExistentId = UUID.randomUUID();

    given()
        .when()
        .get("/api/v1/cases/{caseId}", nonExistentId)
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Case instance not found"))
        .body("status", equalTo(404));
  }

  @Test
  void getContext_returns200WithFullContext() {
    // Start a case with context
    Map<String, Object> startRequest =
        Map.of(
            "definition",
            Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
            "context",
            Map.of("documentId", "DOC-789", "submittedBy", "bob@example.com"));

    String caseId =
        given()
            .contentType(ContentType.JSON)
            .body(startRequest)
            .when()
            .post("/api/v1/cases")
            .then()
            .statusCode(200)
            .extract()
            .path("caseId");

    // Get context
    given()
        .when()
        .get("/api/v1/cases/{caseId}/context", caseId)
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("$", hasKey("documentId"))
        .body("$", hasKey("submittedBy"))
        .body("documentId", equalTo("DOC-789"))
        .body("submittedBy", equalTo("bob@example.com"));
  }

  @Test
  void getContextPath_simpleProperty_returns200() {
    // Start case with context
    Map<String, Object> startRequest =
        Map.of(
            "definition",
            Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
            "context",
            Map.of("documentId", "DOC-999", "approved", true));

    String caseId =
        given()
            .contentType(ContentType.JSON)
            .body(startRequest)
            .when()
            .post("/api/v1/cases")
            .then()
            .statusCode(200)
            .extract()
            .path("caseId");

    // Get specific path
    given()
        .when()
        .get("/api/v1/cases/{caseId}/context/{path}", caseId, "documentId")
        .then()
        .statusCode(200)
        .body(equalTo("DOC-999"));
  }

  @Test
  void getContextPath_nonExistentPath_returns200WithNull() {
    // Start case with context
    Map<String, Object> startRequest =
        Map.of(
            "definition",
            Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
            "context",
            Map.of("documentId", "DOC-123"));

    String caseId =
        given()
            .contentType(ContentType.JSON)
            .body(startRequest)
            .when()
            .post("/api/v1/cases")
            .then()
            .statusCode(200)
            .extract()
            .path("caseId");

    // Get non-existent path
    given()
        .when()
        .get("/api/v1/cases/{caseId}/context/{path}", caseId, "nonExistent")
        .then()
        .statusCode(200);
  }
}
