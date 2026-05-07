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

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SignalResourceTest {

  @Test
  void sendSignal_validRequest_returns202() {
    UUID caseId = UUID.randomUUID();

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
}
