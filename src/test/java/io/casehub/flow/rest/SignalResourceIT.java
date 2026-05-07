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
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SignalResourceIT {

  @Test
  void sendSignal_nonExistentCase_returns404() {
    UUID nonExistentCaseId = UUID.randomUUID();

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
        .post("/api/v1/cases/{caseId}/signals", nonExistentCaseId)
        .then()
        .statusCode(404)
        .body("title", equalTo("Case not found"));
  }

  @Test
  void sendSignal_updatesContextAndTriggersWorkers() {
    // 1. Start a test case
    UUID caseId = startTestCase();

    // 2. Send signal
    given()
        .contentType(ContentType.JSON)
        .body(
            """
            {
              "path": "approval.status",
              "value": "approved"
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", caseId)
        .then()
        .statusCode(202);

    // 3. Wait for async worker processing
    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> {
              // 4. Verify context updated
              String contextValue =
                  given()
                      .when()
                      .get("/api/v1/cases/{caseId}/context/approval.status", caseId)
                      .then()
                      .statusCode(200)
                      .extract()
                      .asString();

              assertThat(contextValue).isEqualTo("approved");
            });
  }

  private UUID startTestCase() {
    Map<String, Object> request =
        Map.of(
            "definition",
            Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
            "context",
            Map.of("documentId", "DOC-123", "submittedBy", "alice@example.com"));

    String response =
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/v1/cases")
            .then()
            .statusCode(200)
            .extract()
            .path("caseId");

    return UUID.fromString(response);
  }
}
