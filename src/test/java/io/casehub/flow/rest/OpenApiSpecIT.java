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
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class OpenApiSpecIT extends CaseHubIntegrationTestBase {

  @Test
  void openApiEndpointReturnsValidSpec() {
    given()
        .when()
        .get("/q/openapi?format=JSON")
        .then()
        .statusCode(200);
  }

  @Test
  void openApiSpecContainsApiInfo() {
    Response response = given()
        .when()
        .get("/q/openapi?format=JSON")
        .then()
        .statusCode(200)
        .extract().response();

    Map<String, Object> info = response.jsonPath().getMap("info");

    assertThat(info.get("title")).isEqualTo("CaseHub Flow API");
    assertThat(info.get("version")).isEqualTo("1.0.0");
  }

  @Test
  void openApiSpecContainsAllPaths() {
    Response response = given()
        .when()
        .get("/q/openapi?format=JSON")
        .then()
        .statusCode(200)
        .extract().response();

    Map<String, Object> paths = response.jsonPath().getMap("paths");

    assertThat(paths).containsKey("/api/v1/case-definitions");
    assertThat(paths).containsKey("/api/v1/case-definitions/{namespace}/{name}");
    assertThat(paths).containsKey("/api/v1/case-definitions/{namespace}/{name}/{version}");
    assertThat(paths).containsKey("/api/v1/cases");
    assertThat(paths).containsKey("/api/v1/cases/{caseId}");
    assertThat(paths).containsKey("/api/v1/cases/{caseId}/context");
    assertThat(paths).containsKey("/api/v1/cases/{caseId}/context/{path}");
    assertThat(paths).containsKey("/api/v1/cases/{caseId}/suspend");
    assertThat(paths).containsKey("/api/v1/cases/{caseId}/resume");
    assertThat(paths).containsKey("/api/v1/cases/{caseId}/cancel");
    assertThat(paths).containsKey("/api/v1/cases/{caseId}/signals");
  }

  @Test
  void openApiSpecContainsResponseCodes() {
    Response response = given()
        .when()
        .get("/q/openapi?format=JSON")
        .then()
        .statusCode(200)
        .extract().response();

    // CaseDefinitionResource - listAll
    Map<String, Object> listAllResponses = response.jsonPath()
        .getMap("paths.'/api/v1/case-definitions'.get.responses");
    assertThat(listAllResponses).containsKey("200");
    assertThat(listAllResponses).containsKey("400");

    // CaseInstanceResource - startCase
    Map<String, Object> startCaseResponses = response.jsonPath()
        .getMap("paths.'/api/v1/cases'.post.responses");
    assertThat(startCaseResponses).containsKey("200");
    assertThat(startCaseResponses).containsKey("400");
    assertThat(startCaseResponses).containsKey("404");
    assertThat(startCaseResponses).containsKey("500");

    // CaseControlResource - suspend
    Map<String, Object> suspendResponses = response.jsonPath()
        .getMap("paths.'/api/v1/cases/{caseId}/suspend'.post.responses");
    assertThat(suspendResponses).containsKey("202");
    assertThat(suspendResponses).containsKey("404");
    assertThat(suspendResponses).containsKey("409");
    assertThat(suspendResponses).containsKey("500");

    // EventLogResource - getEventLog
    Map<String, Object> eventLogResponses = response.jsonPath()
        .getMap("paths.'/api/v1/cases/{caseId}/events'.get.responses");
    assertThat(eventLogResponses).containsKey("200");
    assertThat(eventLogResponses).containsKey("400");
    assertThat(eventLogResponses).containsKey("404");
    assertThat(eventLogResponses).containsKey("500");

    // SignalResource - sendSignal
    Map<String, Object> signalResponses = response.jsonPath()
        .getMap("paths.'/api/v1/cases/{caseId}/signals'.post.responses");
    assertThat(signalResponses).containsKey("202");
    assertThat(signalResponses).containsKey("400");
    assertThat(signalResponses).containsKey("404");
    assertThat(signalResponses).containsKey("500");
  }

  @Test
  void openApiSpecContainsSchemaComponents() {
    Response response = given()
        .when()
        .get("/q/openapi?format=JSON")
        .then()
        .statusCode(200)
        .extract().response();

    Map<String, Object> schemas = response.jsonPath()
        .getMap("components.schemas");

    assertThat(schemas).containsKey("ProblemDetail");
    assertThat(schemas).containsKey("StartCaseRequest");
    assertThat(schemas).containsKey("CaseInstanceResponse");
    assertThat(schemas).containsKey("CaseControlRequest");
    assertThat(schemas).containsKey("CaseControlResponse");
    assertThat(schemas).containsKey("SendSignalRequest");
    assertThat(schemas).containsKey("SignalResponse");
    assertThat(schemas).containsKey("EventLogEntryResponse");
  }

  @Test
  void swaggerUiIsAvailable() {
    given()
        .when()
        .get("/q/swagger-ui")
        .then()
        .statusCode(200)
        .body(containsString("swagger-ui"));
  }
}
