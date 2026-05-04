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
package io.casehub.flow;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.model.Binding;
import io.casehub.api.model.Capability;
import io.casehub.api.model.CaseDefinition;
import io.casehub.api.model.ContextChangeTrigger;
import io.casehub.api.model.Goal;
import io.casehub.api.model.GoalExpression;
import io.casehub.api.model.GoalKind;
import io.casehub.api.model.Milestone;
import io.casehub.api.model.Worker;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Integration test for case definition discovery REST API endpoints.
 *
 * <p>Verifies:
 * <ul>
 *   <li>GET /api/v1/case-definitions — list all registered definitions
 *   <li>GET /api/v1/case-definitions/{namespace}/{name} — get all versions of specific definition
 *   <li>GET /api/v1/case-definitions/{namespace}/{name}/{version} — get specific version
 *   <li>Proper 404 responses for non-existent definitions
 *   <li>RFC 7807 problem details format for errors
 * </ul>
 *
 * <p>Uses test CDI beans that get auto-registered on startup via {@link
 * io.casehub.engine.internal.engine.DefaultCaseDefinitionRegistry}.
 */
@QuarkusTest
class CaseDefinitionResourceTest {

  @Test
  void listAllCaseDefinitions_returnsRegisteredDefinitions() {
    given()
        .when()
        .get("/api/v1/case-definitions")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("content", hasSize(5)) // Three CDI beans + one classpath class + one YAML definition
        .body("page", equalTo(1))
        .body("size", equalTo(20))
        .body("totalElements", equalTo(5))
        .body("totalPages", equalTo(1))
        .body("content[0].namespace", notNullValue())
        .body("content[0].name", notNullValue())
        .body("content[0].version", notNullValue());
  }

  @Test
  void listAllCaseDefinitions_includesMetadata() {
    given()
        .when()
        .get("/api/v1/case-definitions")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("content.size()", equalTo(5)) // Three CDI beans + one classpath class + one YAML
        .body("content.findAll { it.name == 'Document Approval' }.size()", equalTo(2))
        .body("content.findAll { it.name == 'Document Approval' && it.version == '1.0.0' }.size()", equalTo(1))
        .body("content.find { it.name == 'Document Approval' && it.version == '1.0.0' }.namespace", equalTo("test-api"))
        .body("content.find { it.name == 'Document Approval' && it.version == '1.0.0' }.capabilities", notNullValue())
        .body("content.find { it.name == 'Document Approval' && it.version == '1.0.0' }.workers", notNullValue())
        .body("content.find { it.name == 'Document Approval' && it.version == '1.0.0' }.bindings", notNullValue())
        .body("content.find { it.name == 'Document Approval' && it.version == '1.0.0' }.milestones", notNullValue())
        .body("content.find { it.name == 'Document Approval' && it.version == '1.0.0' }.goals", notNullValue());
  }

  @Test
  void getCaseDefinitionByNamespaceAndName_returnsAllVersions() {
    given()
        .when()
        .get("/api/v1/case-definitions/test-api/Document%20Approval")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("$", hasSize(2)) // Two versions: 1.0.0 and 2.0.0
        .body("[0].namespace", equalTo("test-api"))
        .body("[0].name", equalTo("Document Approval"))
        .body("[1].namespace", equalTo("test-api"))
        .body("[1].name", equalTo("Document Approval"));
  }

  @Test
  void getCaseDefinitionByNamespaceAndName_returnsNotFoundForNonExistent() {
    given()
        .when()
        .get("/api/v1/case-definitions/non-existent/unknown")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Case definition not found"))
        .body("status", equalTo(404))
        .body("detail", notNullValue());
  }

  @Test
  void getCaseDefinitionByNamespaceNameVersion_returnsSpecificVersion() {
    given()
        .when()
        .get("/api/v1/case-definitions/test-api/Document%20Approval/1.0.0")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("namespace", equalTo("test-api"))
        .body("name", equalTo("Document Approval"))
        .body("version", equalTo("1.0.0"))
        .body("capabilities", notNullValue())
        .body("workers", notNullValue())
        .body("bindings", notNullValue())
        .body("milestones", notNullValue())
        .body("goals", notNullValue());
  }

  @Test
  void getCaseDefinitionByNamespaceNameVersion_returnsNotFoundForNonExistentVersion() {
    given()
        .when()
        .get("/api/v1/case-definitions/test-api/Document%20Approval/99.0.0")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Case definition not found"))
        .body("status", equalTo(404))
        .body("detail", notNullValue());
  }

  @Test
  void getCaseDefinitionByNamespaceNameVersion_returnsNotFoundForNonExistentDefinition() {
    given()
        .when()
        .get("/api/v1/case-definitions/unknown-namespace/unknown-name/1.0.0")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Case definition not found"))
        .body("status", equalTo(404));
  }

  @Test
  void yamlCaseDefinitionIsLoaded() {
    given()
        .when()
        .get("/api/v1/case-definitions/test-yaml/YAML%20Test%20Case/1.0.0")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("namespace", equalTo("test-yaml"))
        .body("name", equalTo("YAML Test Case"))
        .body("version", equalTo("1.0.0"))
        .body("capabilities", notNullValue())
        .body("workers", notNullValue())
        .body("bindings", notNullValue())
        .body("goals", notNullValue());
  }

  @Test
  void plainCaseHubClassIsDiscoveredWithoutCdiBeanAnnotation() {
    given()
        .when()
        .get("/api/v1/case-definitions/test-classpath/Classpath%20Only%20Case/1.0.0")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("namespace", equalTo("test-classpath"))
        .body("name", equalTo("Classpath Only Case"))
        .body("version", equalTo("1.0.0"))
        .body("capabilities", notNullValue())
        .body("workers", notNullValue())
        .body("bindings", notNullValue());
  }

  @Test
  void listAllCaseDefinitions_withCustomPageSize() {
    given()
        .queryParam("page", 1)
        .queryParam("size", 2)
        .when()
        .get("/api/v1/case-definitions")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("content", hasSize(2))
        .body("page", equalTo(1))
        .body("size", equalTo(2))
        .body("totalElements", equalTo(5))
        .body("totalPages", equalTo(3));
  }

  @Test
  void listAllCaseDefinitions_withSecondPage() {
    given()
        .queryParam("page", 2)
        .queryParam("size", 2)
        .when()
        .get("/api/v1/case-definitions")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("content", hasSize(2))
        .body("page", equalTo(2))
        .body("size", equalTo(2))
        .body("totalElements", equalTo(5))
        .body("totalPages", equalTo(3));
  }

  @Test
  void listAllCaseDefinitions_withInvalidPageNumber() {
    given()
        .queryParam("page", 0)
        .queryParam("size", 20)
        .when()
        .get("/api/v1/case-definitions")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid page parameter"))
        .body("status", equalTo(400))
        .body("detail", equalTo("Page number must be greater than or equal to 1"));
  }

  @Test
  void listAllCaseDefinitions_withInvalidPageSize() {
    given()
        .queryParam("page", 1)
        .queryParam("size", 101)
        .when()
        .get("/api/v1/case-definitions")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid size parameter"))
        .body("status", equalTo(400))
        .body("detail", equalTo("Page size must be between 1 and 100"));
  }

  @Test
  void listAllCaseDefinitions_withNegativePageSize() {
    given()
        .queryParam("page", 1)
        .queryParam("size", 0)
        .when()
        .get("/api/v1/case-definitions")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid size parameter"))
        .body("status", equalTo(400))
        .body("detail", equalTo("Page size must be between 1 and 100"));
  }

  // ------------------------------------------------------------------ //
  // Test CDI beans - auto-registered on startup                        //
  // ------------------------------------------------------------------ //

  /**
   * Document approval case - version 1.0.0
   *
   * <p>Includes milestones, goals, and bindings to verify complete metadata serialization.
   */
  @ApplicationScoped
  public static class DocumentApprovalV1CaseHub extends CaseHub {

    private final Capability approveCapability =
        Capability.builder()
            .name("approveDocument")
            .description("Approve a document")
            .inputSchema("{ documentId: .documentId, status: .status }")
            .outputSchema("{ approved: true, status: .status }")
            .build();

    private final Milestone submittedMilestone =
        Milestone.builder()
            .name("documentSubmitted")
            .description("Document has been submitted for approval")
            .entryCriteria(".status == \"submitted\"")
            .completionCriteria(".status == \"approved\"")
            .build();

    private final Goal approvalGoal =
        Goal.builder()
            .name("documentApproved")
            .description("Document is approved")
            .condition(".status == \"approved\"")
            .kind(GoalKind.SUCCESS)
            .build();

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder()
          .namespace("test-api")
          .name("Document Approval")
          .version("1.0.0")
          .capabilities(approveCapability)
          .workers(
              Worker.builder()
                  .name("approver-worker")
                  .description("Approves documents")
                  .capabilities(approveCapability)
                  .function(input -> Map.of("approved", true, "status", "approved"))
                  .build())
          .bindings(
              Binding.builder()
                  .name("approve-on-submit")
                  .capability(approveCapability)
                  .on(new ContextChangeTrigger(".status == \"submitted\""))
                  .build())
          .milestones(submittedMilestone)
          .goals(approvalGoal)
          .completion(GoalExpression.allOf(approvalGoal))
          .build();
    }
  }

  /**
   * Document approval case - version 2.0.0
   *
   * <p>Same namespace and name as V1, different version to test multi-version retrieval.
   */
  @ApplicationScoped
  public static class DocumentApprovalV2CaseHub extends CaseHub {

    private final Capability approveCapability =
        Capability.builder()
            .name("approveDocument")
            .inputSchema("{ documentId: .documentId, status: .status }")
            .outputSchema("{ approved: true, status: .status }")
            .build();

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder()
          .namespace("test-api")
          .name("Document Approval")
          .version("2.0.0")
          .capabilities(approveCapability)
          .workers(
              Worker.builder()
                  .name("approver-worker-v2")
                  .capabilities(approveCapability)
                  .function(input -> Map.of("approved", true, "status", "approved"))
                  .build())
          .bindings(
              Binding.builder()
                  .name("approve-on-submit")
                  .capability(approveCapability)
                  .on(new ContextChangeTrigger(".status == \"submitted\""))
                  .build())
          .build();
    }
  }

  /**
   * Invoice processing case - different namespace/name
   *
   * <p>Third definition to verify list endpoint returns multiple distinct definitions.
   */
  @ApplicationScoped
  public static class InvoiceProcessingCaseHub extends CaseHub {

    private final Capability processCapability =
        Capability.builder()
            .name("processInvoice")
            .inputSchema("{ invoiceId: .invoiceId, amount: .amount }")
            .outputSchema("{ processed: true }")
            .build();

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder()
          .namespace("test-api")
          .name("Invoice Processing")
          .version("1.0.0")
          .capabilities(processCapability)
          .workers(
              Worker.builder()
                  .name("invoice-processor")
                  .capabilities(processCapability)
                  .function(input -> Map.of("processed", true))
                  .build())
          .bindings(
              Binding.builder()
                  .name("process-on-receive")
                  .capability(processCapability)
                  .on(new ContextChangeTrigger(".status == \"received\""))
                  .build())
          .build();
    }
  }

  /**
   * Intentionally not a CDI bean.
   *
   * <p>Flow should discover concrete CaseHub subclasses from the classpath and register the
   * definition even when the class has no CDI scope annotation.
   */
  public static class ClasspathOnlyCaseHub extends CaseHub {

    private final Capability classifyCapability =
        Capability.builder()
            .name("classifyDocument")
            .inputSchema("{ documentId: .documentId }")
            .outputSchema("{ classified: true }")
            .build();

    @Override
    public CaseDefinition getDefinition() {
      return CaseDefinition.builder()
          .namespace("test-classpath")
          .name("Classpath Only Case")
          .version("1.0.0")
          .capabilities(classifyCapability)
          .workers(
              Worker.builder()
                  .name("document-classifier")
                  .capabilities(classifyCapability)
                  .function(input -> Map.of("classified", true))
                  .build())
          .bindings(
              Binding.builder()
                  .name("classify-on-receive")
                  .capability(classifyCapability)
                  .on(new ContextChangeTrigger(".status == \"received\""))
                  .build())
          .build();
    }
  }
}
