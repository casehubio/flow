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
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EventLogResourceIT {

  @Test
  void getEventLog_nonExistentCase_returns404() {
    UUID randomCaseId = UUID.randomUUID();

    given()
        .when()
        .get("/api/v1/cases/{caseId}/events", randomCaseId)
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Case instance not found"))
        .body("status", equalTo(404))
        .body("detail", notNullValue());
  }

  @Test
  void getEventLog_invalidPage_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("page", 0)
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid pagination parameters"))
        .body("status", equalTo(400))
        .body("detail", notNullValue());
  }

  @Test
  void getEventLog_negativeSize_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("size", -1)
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid pagination parameters"))
        .body("status", equalTo(400))
        .body("detail", notNullValue());
  }

  @Test
  void getEventLog_sizeTooLarge_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("size", 1001)
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid pagination parameters"))
        .body("status", equalTo(400))
        .body("detail", notNullValue());
  }

  @Test
  void getEventLog_invalidEventType_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("eventType", "INVALID_EVENT")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid filter parameter"))
        .body("status", equalTo(400))
        .body("detail", notNullValue());
  }

  @Test
  void getEventLog_invalidStreamType_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("streamType", "INVALID_STREAM")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid filter parameter"))
        .body("status", equalTo(400))
        .body("detail", notNullValue());
  }

  @Test
  void getEventLog_sizeZero_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("size", 0)
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid pagination parameters"))
        .body("status", equalTo(400))
        .body("detail", notNullValue());
  }
}
