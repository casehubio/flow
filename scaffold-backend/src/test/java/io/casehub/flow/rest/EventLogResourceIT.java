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
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EventLogResourceIT extends CaseHubIntegrationTestBase {

  @Test
  void getEventLog_nonExistentCase_returns404() {
    UUID randomCaseId = UUID.randomUUID();

    given()
        .when()
        .get("/api/v1/cases/{caseId}/events", randomCaseId)
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Not found"))
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
        .body("title", equalTo("Invalid request"))
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
        .body("title", equalTo("Invalid request"))
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
        .body("title", equalTo("Invalid request"))
        .body("status", equalTo(400))
        .body("detail", notNullValue());
  }

  @Test
  void getEventLog_invalidEventType_returns400() {
    UUID caseId = startTestCase();

    given()
        .queryParam("eventType", "INVALID_EVENT")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid request"))
        .body("status", equalTo(400))
        .body("detail", notNullValue());
  }

  @Test
  void getEventLog_invalidStreamType_returns400() {
    UUID caseId = startTestCase();

    given()
        .queryParam("streamType", "INVALID_STREAM")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid request"))
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
        .body("title", equalTo("Invalid request"))
        .body("status", equalTo(400))
        .body("detail", notNullValue());
  }

  @Test
  void getEventLog_defaultPagination_returnsEvents() {
    UUID caseId = startTestCase();
    waitForEvents(caseId);

    given()
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200)
        .body("page", equalTo(1))
        .body("size", equalTo(50))
        .body("totalElements", greaterThan(0))
        .body("items", hasSize(greaterThan(0)))
        .body("items[0].eventType", notNullValue())
        .body("items[0].streamType", notNullValue())
        .body("items[0].timestamp", notNullValue());
  }

  @Test
  void getEventLog_customPagination_returnsCorrectPage() {
    UUID caseId = startTestCase();
    waitForEvents(caseId);

    given()
        .queryParam("page", 1)
        .queryParam("size", 5)
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200)
        .body("page", equalTo(1))
        .body("size", equalTo(5))
        .body("items.size()", lessThanOrEqualTo(5))
        .body("items[0].eventType", notNullValue())
        .body("items[0].streamType", notNullValue())
        .body("items[0].timestamp", notNullValue());
  }

  @Test
  void getEventLog_filterByEventType_returnsOnlyMatchingEvents() {
    UUID caseId = startTestCase();
    waitForEvents(caseId);

    // Filter by a specific event type that exists in the test case
    given()
        .queryParam("eventType", "CASE_STARTED")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200)
        .body("items", hasSize(greaterThan(0)))
        .body("items.eventType", everyItem(equalTo("CASE_STARTED")));
  }

  @Test
  void getEventLog_filterByMultipleEventTypes_returnsMatchingEvents() {
    UUID caseId = startTestCase();
    waitForEvents(caseId);

    given()
        .queryParam("eventType", "CASE_STARTED")
        .queryParam("eventType", "CASE_STATUS_CHANGED")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200)
        .body("items", hasSize(greaterThan(0)))
        .body(
            "items.eventType",
            everyItem(anyOf(equalTo("CASE_STARTED"), equalTo("CASE_STATUS_CHANGED"))));
  }

  @Test
  void getEventLog_filterByStreamType_returnsMatchingEvents() {
    UUID caseId = startTestCase();
    waitForEvents(caseId);

    given()
        .queryParam("streamType", "CASE")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200)
        .body("items", hasSize(greaterThan(0)))
        .body("items.streamType", everyItem(equalTo("CASE")));
  }

  @Test
  void getEventLog_combinedFilters_returnsMatchingEvents() {
    UUID caseId = startTestCase();
    waitForEvents(caseId);

    given()
        .queryParam("eventType", "CASE_STARTED")
        .queryParam("streamType", "CASE")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200)
        .body("items", hasSize(greaterThan(0)))
        .body("items.eventType", everyItem(equalTo("CASE_STARTED")))
        .body("items.streamType", everyItem(equalTo("CASE")));
  }
}
