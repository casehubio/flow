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
import static org.awaitility.Awaitility.await;

import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CaseControlResourceIT extends CaseHubIntegrationTestBase {

  private String getCaseStatus(UUID caseId) {
    return given()
        .when()
        .get("/api/v1/cases/{caseId}", caseId)
        .then()
        .statusCode(200)
        .extract()
        .path("status");
  }

  @Test
  void testSuspendNonExistentCase() {
    UUID randomCaseId = UUID.randomUUID();

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/suspend", randomCaseId)
        .then()
        .statusCode(404);
  }

  @Test
  void testSuspendRunningCase() {
    UUID caseId = startTestCase();

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/suspend", caseId)
        .then()
        .statusCode(200);

    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> {
              String status = getCaseStatus(caseId);
              assert status.equals("SUSPENDED") || status.equals("WAITING");
            });
  }

  @Test
  void testResumeSuspendedCase() {
    UUID caseId = startTestCase();

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/suspend", caseId)
        .then()
        .statusCode(200);

    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> {
              String status = getCaseStatus(caseId);
              assert status.equals("SUSPENDED");
            });

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/resume", caseId)
        .then()
        .statusCode(200);

    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> {
              String status = getCaseStatus(caseId);
              assert status.equals("RUNNING");
            });
  }

  @Test
  void testResumeNonSuspendedCase() {
    UUID caseId = startTestCase();

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/resume", caseId)
        .then()
        .statusCode(409);
  }

  @Test
  void testSuspendAlreadySuspendedCase() {
    UUID caseId = startTestCase();

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/suspend", caseId)
        .then()
        .statusCode(200);

    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> {
              String status = getCaseStatus(caseId);
              assert status.equals("SUSPENDED");
            });

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/suspend", caseId)
        .then()
        .statusCode(409);
  }

  @Test
  void testCancelRunningCase() {
    UUID caseId = startTestCase();

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/cancel", caseId)
        .then()
        .statusCode(200);

    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> {
              String status = getCaseStatus(caseId);
              assert status.equals("CANCELLED")
                  || status.equals("FAULTED")
                  || status.equals("COMPLETED");
            });
  }

  @Test
  void testFullWorkflow() {
    UUID caseId = startTestCase();

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/suspend", caseId)
        .then()
        .statusCode(200);

    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> {
              String status = getCaseStatus(caseId);
              assert status.equals("SUSPENDED");
            });

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/resume", caseId)
        .then()
        .statusCode(200);

    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> {
              String status = getCaseStatus(caseId);
              assert status.equals("RUNNING");
            });

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/{caseId}/cancel", caseId)
        .then()
        .statusCode(200);

    await()
        .atMost(10, SECONDS)
        .untilAsserted(
            () -> {
              String status = getCaseStatus(caseId);
              assert status.equals("CANCELLED")
                  || status.equals("FAULTED")
                  || status.equals("COMPLETED");
            });
  }
}
