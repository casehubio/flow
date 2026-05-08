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
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;

/**
 * Base class for integration tests that need to create and manage case instances. Provides common
 * utilities for starting test cases and ensures proper cleanup after each test.
 */
public abstract class CaseHubIntegrationTestBase {

  @Inject protected CaseHubRuntime caseHubRuntime;
  @Inject protected Instance<CaseHub> caseHubs;

  private List<UUID> createdCases = new ArrayList<>();

  /**
   * Start a test case instance. Cases are automatically cleaned up after each test.
   *
   * @return the UUID of the started case
   * @throws RuntimeException if the case fails to start
   */
  protected UUID startTestCase() {
    CaseDefinition definition = caseHubs.stream().findFirst().orElseThrow().getDefinition();
    Map<String, Object> context = new HashMap<>();
    try {
      UUID caseId = caseHubRuntime.startCase(definition, context).toCompletableFuture().get();
      createdCases.add(caseId);
      return caseId;
    } catch (Exception e) {
      throw new RuntimeException("Failed to start test case", e);
    }
  }

  /**
   * Wait for a case to generate events.
   *
   * @param caseId the case ID to wait for
   */
  protected void waitForEvents(UUID caseId) {
    await()
        .atMost(10, SECONDS)
        .until(
            () -> {
              var response =
                  given()
                      .when()
                      .get("/api/v1/cases/{caseId}/events", caseId)
                      .then()
                      .statusCode(200)
                      .extract()
                      .path("totalElements");
              return (Integer) response > 0;
            });
  }

  @AfterEach
  void cleanupCases() {
    // Clean up all cases created during this test
    createdCases.forEach(
        caseId -> {
          try {
            // Terminate the case if it's still running
            caseHubRuntime.cancelCase(caseId);
          } catch (Exception e) {
            // Log but don't fail test cleanup
            System.err.println("Failed to cleanup case: " + caseId + ", error: " + e.getMessage());
          }
        });
    createdCases.clear();
  }
}
