package io.casehub.flow.rest;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.engine.common.spi.CaseDefinitionRegistry;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;

public abstract class CaseHubIntegrationTestBase {

  @Inject protected CaseHubRuntime caseHubRuntime;
  @Inject protected CaseDefinitionRegistry definitionRegistry;

  private List<UUID> createdCases = new ArrayList<>();

  protected UUID startTestCase() {
    var definition = definitionRegistry.allDefinitions().stream().findFirst().orElseThrow();
    Map<String, Object> context = new HashMap<>();
    try {
      UUID caseId = caseHubRuntime.startCase(definition, context).toCompletableFuture().get();
      createdCases.add(caseId);
      return caseId;
    } catch (Exception e) {
      throw new RuntimeException("Failed to start test case", e);
    }
  }

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
    createdCases.forEach(
        caseId -> {
          try {
            caseHubRuntime.cancelCase(caseId);
          } catch (Exception e) {
            System.err.println("Failed to cleanup case: " + caseId + ", error: " + e.getMessage());
          }
        });
    createdCases.clear();
  }
}
