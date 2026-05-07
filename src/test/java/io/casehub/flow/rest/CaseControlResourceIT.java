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

import io.casehub.api.engine.CaseHub;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.CaseDefinition;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CaseControlResourceIT {

  @Inject CaseHubRuntime caseHubRuntime;
  @Inject Instance<CaseHub> caseHubs;

  private UUID startTestCase() {
    CaseDefinition definition = caseHubs.stream().findFirst().orElseThrow().getDefinition();
    Map<String, Object> context = new HashMap<>();
    try {
      return caseHubRuntime.startCase(definition, context).toCompletableFuture().get();
    } catch (Exception e) {
      throw new RuntimeException("Failed to start test case", e);
    }
  }

  private String getCaseStatus(UUID caseId) {
    return given()
        .when()
        .get("/api/v1/cases/{caseId}", caseId)
        .then()
        .statusCode(200)
        .extract()
        .path("status");
  }
}
