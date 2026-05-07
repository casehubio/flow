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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.flow.exception.CaseInstanceNotFoundException;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.http.ContentType;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SignalResourceTest {

  @InjectMock CaseHubRuntime caseHubRuntime;

  @Test
  void sendSignal_validRequest_returns202() {
    UUID caseId = UUID.randomUUID();

    // Mock query to validate case exists
    when(caseHubRuntime.query(eq(caseId), eq("."), eq(Object.class)))
        .thenReturn(CompletableFuture.completedFuture(new Object()));

    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "path": "approvals.user",
              "value": {"approved": true}
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", caseId)
        .then()
        .statusCode(202)
        .body("caseId", equalTo(caseId.toString()))
        .body("status", equalTo("accepted"))
        .body("message", containsString("queued"));

    verify(caseHubRuntime).signal(eq(caseId), eq("approvals.user"), any());
  }

  @Test
  void sendSignal_nullRequestBody_returns400() {
    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/api/v1/cases/{caseId}/signals", UUID.randomUUID())
        .then()
        .statusCode(400)
        .body("title", equalTo("Invalid request"))
        .body("status", equalTo(400));
  }

  @Test
  void sendSignal_nullPath_returns400() {
    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "path": null,
              "value": {"approved": true}
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", UUID.randomUUID())
        .then()
        .statusCode(400)
        .body("title", equalTo("Invalid request"))
        .body("status", equalTo(400));
  }

  @Test
  void sendSignal_nullValue_returns400() {
    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "path": "approvals.user",
              "value": null
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", UUID.randomUUID())
        .then()
        .statusCode(400)
        .body("title", equalTo("Invalid request"));
  }

  @Test
  void sendSignal_caseNotFound_returns404() {
    UUID caseId = UUID.randomUUID();
    doThrow(new CaseInstanceNotFoundException(caseId))
        .when(caseHubRuntime)
        .query(eq(caseId), eq("."), eq(Object.class));

    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "path": "test.path",
              "value": "test"
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", caseId)
        .then()
        .statusCode(404)
        .body("title", equalTo("Case not found"));
  }

  @Test
  void sendSignal_runtimeException_returns500() {
    UUID caseId = UUID.randomUUID();
    doThrow(new RuntimeException("Database error"))
        .when(caseHubRuntime)
        .query(eq(caseId), eq("."), eq(Object.class));

    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "path": "test.path",
              "value": "test"
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", caseId)
        .then()
        .statusCode(500)
        .body("title", equalTo("Internal server error"))
        .body("detail", containsString("Failed to send signal"));
  }
}
