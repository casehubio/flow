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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthEndpointIT extends CaseHubIntegrationTestBase {

  @Test
  void livenessReturns200() {
    given()
        .when()
        .get("/q/health/live")
        .then()
        .statusCode(200)
        .body("status", is("UP"));
  }

  @Test
  void readinessReturns200WhenHealthy() {
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(200)
        .body("status", is("UP"));
  }

  @Test
  void readinessContainsCaseEngineCheck() {
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(200)
        .body("checks.name", hasItem("Case engine"))
        .body("checks.find { it.name == 'Case engine' }.status", is("UP"));
  }

  @Test
  void readinessContainsDatabaseCheck() {
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(200)
        .body("checks.find { it.name.contains('Reactive') }.status", is("UP"));
  }
}
