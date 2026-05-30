# OpenAPI Specification and Swagger UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add OpenAPI 3.0 spec generation and Swagger UI to document all 11 REST API v1 endpoints with full schema annotations.

**Architecture:** Add `quarkus-smallrye-openapi` dependency, configure API metadata in application.properties, annotate all 5 REST resources with @Tag/@Operation/@APIResponse/@Parameter, annotate all 9 DTOs with @Schema, and write integration tests validating the generated spec.

**Tech Stack:** Quarkus 3.32.2, SmallRye OpenAPI, MicroProfile OpenAPI annotations, Swagger UI, JUnit 5, REST-assured

**Spec:** `docs/superpowers/specs/2026-05-29-openapi-swagger-design.md`
**Issue:** #11 (epic #1)

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `pom.xml` | Modify | Add `quarkus-smallrye-openapi` dependency |
| `src/main/resources/application.properties` | Modify | Add OpenAPI metadata + Swagger UI config |
| `src/main/java/io/casehub/flow/rest/dto/ProblemDetail.java` | Modify | Add @Schema annotations |
| `src/main/java/io/casehub/flow/rest/dto/StartCaseRequest.java` | Modify | Add @Schema annotations |
| `src/main/java/io/casehub/flow/rest/dto/CaseInstanceResponse.java` | Modify | Add @Schema annotations |
| `src/main/java/io/casehub/flow/rest/dto/CaseControlRequest.java` | Modify | Add @Schema annotations |
| `src/main/java/io/casehub/flow/rest/dto/CaseControlResponse.java` | Modify | Add @Schema annotations |
| `src/main/java/io/casehub/flow/rest/dto/SendSignalRequest.java` | Modify | Add @Schema annotations |
| `src/main/java/io/casehub/flow/rest/dto/SignalResponse.java` | Modify | Add @Schema annotations |
| `src/main/java/io/casehub/flow/rest/dto/EventLogEntryResponse.java` | Modify | Add @Schema annotations |
| `src/main/java/io/casehub/flow/rest/dto/PagedResponse.java` | Modify | Add @Schema annotations |
| `src/main/java/io/casehub/flow/rest/CaseDefinitionResource.java` | Modify | Add @Tag, @Operation, @APIResponse, @Parameter |
| `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java` | Modify | Add @Tag, @Operation, @APIResponse, @Parameter |
| `src/main/java/io/casehub/flow/rest/CaseControlResource.java` | Modify | Add @Tag, @Operation, @APIResponse, @Parameter |
| `src/main/java/io/casehub/flow/rest/EventLogResource.java` | Modify | Add @Tag, @Operation, @APIResponse, @Parameter |
| `src/main/java/io/casehub/flow/rest/SignalResource.java` | Modify | Add @Tag, @Operation, @APIResponse, @Parameter |
| `src/test/java/io/casehub/flow/rest/OpenApiSpecIT.java` | Create | Integration tests for OpenAPI spec validation |

---

### Task 1: Add dependency and configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`

- [ ] **Step 1: Add quarkus-smallrye-openapi dependency to pom.xml**

Add the following dependency after `quarkus-smallrye-health` (around line 88) in `pom.xml`:

```xml
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-smallrye-openapi</artifactId>
        </dependency>
```

No version needed — managed by the Quarkus BOM.

- [ ] **Step 2: Add OpenAPI configuration to application.properties**

Append the following to the end of `src/main/resources/application.properties`:

```properties

# ============================================================================
# OpenAPI / Swagger UI
# ============================================================================
mp.openapi.extensions.smallrye.info.title=CaseHub Flow API
mp.openapi.extensions.smallrye.info.version=1.0.0
mp.openapi.extensions.smallrye.info.description=REST API for CaseHub case management engine
mp.openapi.extensions.smallrye.info.contact.name=CaseHub Team
mp.openapi.extensions.smallrye.info.contact.url=https://github.com/casehubio/flow

# Swagger UI: dev/test only
quarkus.swagger-ui.always-include=false
%dev.quarkus.swagger-ui.always-include=true
%test.quarkus.swagger-ui.always-include=true
```

- [ ] **Step 3: Verify the dependency resolves**

Run:
```bash
./mvnw dependency:resolve -pl . -q
```

Expected: exits 0 with no errors.

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/main/resources/application.properties
git commit -m "feat: add quarkus-smallrye-openapi dependency and configuration

Refs #11"
```

---

### Task 2: Annotate all DTOs with @Schema

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/dto/ProblemDetail.java`
- Modify: `src/main/java/io/casehub/flow/rest/dto/StartCaseRequest.java`
- Modify: `src/main/java/io/casehub/flow/rest/dto/CaseInstanceResponse.java`
- Modify: `src/main/java/io/casehub/flow/rest/dto/CaseControlRequest.java`
- Modify: `src/main/java/io/casehub/flow/rest/dto/CaseControlResponse.java`
- Modify: `src/main/java/io/casehub/flow/rest/dto/SendSignalRequest.java`
- Modify: `src/main/java/io/casehub/flow/rest/dto/SignalResponse.java`
- Modify: `src/main/java/io/casehub/flow/rest/dto/EventLogEntryResponse.java`
- Modify: `src/main/java/io/casehub/flow/rest/dto/PagedResponse.java`

- [ ] **Step 1: Annotate ProblemDetail**

Replace the record declaration in `src/main/java/io/casehub/flow/rest/dto/ProblemDetail.java`. Add `import org.eclipse.microprofile.openapi.annotations.media.Schema;` to imports and change the record to:

```java
@Schema(description = "RFC 7807 Problem Details error response")
public record ProblemDetail(
    @Schema(description = "Short human-readable error summary", example = "Case not found")
    String title,
    @Schema(description = "HTTP status code", example = "404")
    int status,
    @Schema(description = "Detailed human-readable explanation",
            example = "Case instance abc-123 not found")
    String detail) {}
```

- [ ] **Step 2: Annotate StartCaseRequest**

Add `import org.eclipse.microprofile.openapi.annotations.media.Schema;` to `src/main/java/io/casehub/flow/rest/dto/StartCaseRequest.java` and annotate:

```java
@Schema(description = "Request to start a new case instance")
public record StartCaseRequest(
    @Schema(description = "Case definition reference", required = true)
    @NotNull @Valid CaseDefinitionRef definition,
    @Schema(description = "Initial case context data", nullable = true,
            example = "{\"customer\": {\"name\": \"John\"}}")
    Map<String, Object> context) {

  public StartCaseRequest {
    context = context == null ? Map.of() : Map.copyOf(context);
  }

  @Schema(description = "Reference to a registered case definition")
  public record CaseDefinitionRef(
      @Schema(description = "Case namespace", required = true, example = "acme")
      @NotBlank String namespace,
      @Schema(description = "Case name", required = true, example = "Order Processing")
      @NotBlank String name,
      @Schema(description = "Case version", required = true, example = "1.0.0")
      @NotBlank String version) {}
}
```

- [ ] **Step 3: Annotate CaseInstanceResponse**

Add `import org.eclipse.microprofile.openapi.annotations.media.Schema;` to `src/main/java/io/casehub/flow/rest/dto/CaseInstanceResponse.java` and annotate:

```java
@Schema(description = "Case instance status and metadata")
public record CaseInstanceResponse(
    @Schema(description = "Case instance UUID", required = true,
            example = "550e8400-e29b-41d4-a716-446655440000")
    @NotNull UUID caseId,
    @Schema(description = "Current case status", required = true, example = "RUNNING")
    @NotNull CaseStatus status,
    @Schema(description = "Case namespace", required = true, example = "acme")
    @NotBlank String namespace,
    @Schema(description = "Case name", required = true, example = "Order Processing")
    @NotBlank String name,
    @Schema(description = "Case version", required = true, example = "1.0.0")
    @NotBlank String version,
    @Schema(description = "Case creation timestamp", required = true)
    @NotNull Instant createdAt,
    @Schema(description = "Last update timestamp", required = true)
    @NotNull Instant updatedAt) {}
```

- [ ] **Step 4: Annotate CaseControlRequest**

Add `import org.eclipse.microprofile.openapi.annotations.media.Schema;` to `src/main/java/io/casehub/flow/rest/dto/CaseControlRequest.java` and annotate:

```java
@Schema(description = "Request body for case control operations")
public record CaseControlRequest(
    @Schema(description = "Optional reason for the operation", nullable = true,
            example = "Maintenance window")
    String reason) {}
```

- [ ] **Step 5: Annotate CaseControlResponse**

Add `import org.eclipse.microprofile.openapi.annotations.media.Schema;` to `src/main/java/io/casehub/flow/rest/dto/CaseControlResponse.java` and annotate:

```java
@Schema(description = "Response for case control operations")
public record CaseControlResponse(
    @Schema(description = "Case instance UUID", required = true,
            example = "550e8400-e29b-41d4-a716-446655440000")
    UUID caseId,
    @Schema(description = "Operation performed", required = true, example = "suspend")
    String operation,
    @Schema(description = "Operation status", required = true, example = "accepted")
    String status,
    @Schema(description = "Human-readable status message", required = true,
            example = "Case suspension queued for processing")
    String message) {}
```

- [ ] **Step 6: Annotate SendSignalRequest**

Add `import org.eclipse.microprofile.openapi.annotations.media.Schema;` to `src/main/java/io/casehub/flow/rest/dto/SendSignalRequest.java` and annotate:

```java
@Schema(description = "Request to send a signal to a running case")
public record SendSignalRequest(
    @Schema(description = "Dot-notation context path", required = true,
            example = "approvals.manager")
    @NotBlank String path,
    @Schema(description = "Signal value (String, Number, Boolean, Map, or List)",
            required = true, example = "approved")
    @NotNull Object value) {}
```

- [ ] **Step 7: Annotate SignalResponse**

Add `import org.eclipse.microprofile.openapi.annotations.media.Schema;` to `src/main/java/io/casehub/flow/rest/dto/SignalResponse.java` and annotate:

```java
@Schema(description = "Response after sending a signal")
public record SignalResponse(
    @Schema(description = "Case instance UUID",
            example = "550e8400-e29b-41d4-a716-446655440000")
    UUID caseId,
    @Schema(description = "Signal delivery status", example = "accepted")
    String status,
    @Schema(description = "Human-readable status message",
            example = "Signal delivered to case")
    String message) {}
```

- [ ] **Step 8: Annotate EventLogEntryResponse**

Add `import org.eclipse.microprofile.openapi.annotations.media.Schema;` to `src/main/java/io/casehub/flow/rest/dto/EventLogEntryResponse.java` and annotate:

```java
@Schema(description = "Single event log entry from case audit trail")
public record EventLogEntryResponse(
    @Schema(description = "Type of case event", required = true, example = "CASE_STARTED")
    @NotNull CaseHubEventType eventType,
    @Schema(description = "Event stream type", required = true, example = "CASE")
    @NotNull EventStreamType streamType,
    @Schema(description = "Event timestamp", required = true)
    @NotNull Instant timestamp,
    @Schema(description = "Event payload data", nullable = true)
    JsonNode payload,
    @Schema(description = "Event metadata", nullable = true)
    JsonNode metadata) {}
```

- [ ] **Step 9: Annotate PagedResponse**

Add `import org.eclipse.microprofile.openapi.annotations.media.Schema;` to `src/main/java/io/casehub/flow/rest/dto/PagedResponse.java` and annotate:

```java
@Schema(description = "Paginated response wrapper")
public record PagedResponse<T>(
    @Schema(description = "Page content items")
    List<T> content,
    @Schema(description = "Current page number (1-indexed)", example = "1")
    int page,
    @Schema(description = "Page size", example = "20")
    int size,
    @Schema(description = "Total number of elements", example = "42")
    long totalElements,
    @Schema(description = "Total number of pages", example = "3")
    int totalPages) {}
```

- [ ] **Step 10: Verify compilation**

Run:
```bash
./mvnw compile -pl . -q
```

Expected: exits 0 with no errors.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/dto/
git commit -m "feat: add @Schema annotations to all DTO records

Refs #11"
```

---

### Task 3: Annotate CaseDefinitionResource

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/CaseDefinitionResource.java`

- [ ] **Step 1: Add OpenAPI imports and @Tag**

Add these imports to CaseDefinitionResource.java:

```java
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
```

Add `@Tag` to the class:

```java
@Path("/api/v1/case-definitions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Case Definitions", description = "Query registered case definitions")
public class CaseDefinitionResource {
```

- [ ] **Step 2: Annotate listAll method**

Add before `@GET` on the `listAll` method:

```java
  @GET
  @Operation(summary = "List all case definitions",
             description = "Returns a paginated list of all registered case definitions")
  @Parameter(name = "page", description = "Page number (1-indexed)", example = "1")
  @Parameter(name = "size", description = "Page size (1-100)", example = "20")
  @APIResponse(responseCode = "200", description = "Paginated list of case definitions",
               content = @Content(schema = @Schema(implementation = PagedResponse.class)))
  @APIResponse(responseCode = "400", description = "Invalid pagination parameters",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> listAll(
```

Add import for `import io.casehub.flow.rest.dto.PagedResponse;` if not already present.

- [ ] **Step 3: Annotate getByNamespaceAndName method**

Add before `@GET` on the `getByNamespaceAndName` method:

```java
  @GET
  @Path("/{namespace}/{name}")
  @Operation(summary = "Get definitions by namespace and name",
             description = "Returns all versions of a case definition matching the namespace and name")
  @Parameter(name = "namespace", description = "Case namespace", required = true, example = "acme")
  @Parameter(name = "name", description = "Case name", required = true, example = "Order Processing")
  @APIResponse(responseCode = "200", description = "List of matching case definitions")
  @APIResponse(responseCode = "404", description = "No definitions found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> getByNamespaceAndName(
```

- [ ] **Step 4: Annotate getByNamespaceAndNameAndVersion method**

Add before `@GET` on the `getByNamespaceAndNameAndVersion` method:

```java
  @GET
  @Path("/{namespace}/{name}/{version}")
  @Operation(summary = "Get definition by key",
             description = "Returns a specific case definition by namespace, name, and version")
  @Parameter(name = "namespace", description = "Case namespace", required = true, example = "acme")
  @Parameter(name = "name", description = "Case name", required = true, example = "Order Processing")
  @Parameter(name = "version", description = "Case version", required = true, example = "1.0.0")
  @APIResponse(responseCode = "200", description = "Case definition found")
  @APIResponse(responseCode = "404", description = "Definition not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> getByNamespaceAndNameAndVersion(
```

- [ ] **Step 5: Verify compilation**

Run:
```bash
./mvnw compile -pl . -q
```

Expected: exits 0.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/CaseDefinitionResource.java
git commit -m "feat: add OpenAPI annotations to CaseDefinitionResource

Refs #11"
```

---

### Task 4: Annotate CaseInstanceResource

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`

- [ ] **Step 1: Add OpenAPI imports and @Tag**

Add these imports to CaseInstanceResource.java:

```java
import io.casehub.flow.rest.dto.CaseInstanceResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
```

Add `@Tag` to the class:

```java
@Path("/api/v1/cases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Case Instances", description = "Case instance lifecycle and context")
public class CaseInstanceResource {
```

- [ ] **Step 2: Annotate startCase method**

Add before `@POST` on the `startCase` method:

```java
  @POST
  @Operation(summary = "Start a new case instance",
             description = "Creates and starts a new case instance from a registered definition")
  @RequestBody(description = "Case start request with definition reference and optional context",
               required = true,
               content = @Content(schema = @Schema(implementation = StartCaseRequest.class)))
  @APIResponse(responseCode = "200", description = "Case instance started",
               content = @Content(schema = @Schema(implementation = CaseInstanceResponse.class)))
  @APIResponse(responseCode = "400", description = "Invalid request",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "404", description = "Case definition not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> startCase(StartCaseRequest request) {
```

- [ ] **Step 3: Annotate getCaseInstance method**

```java
  @GET
  @Path("/{caseId}")
  @Operation(summary = "Get case instance by ID",
             description = "Returns the status and metadata of a case instance")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @APIResponse(responseCode = "200", description = "Case instance found",
               content = @Content(schema = @Schema(implementation = CaseInstanceResponse.class)))
  @APIResponse(responseCode = "404", description = "Case instance not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> getCaseInstance(@PathParam("caseId") UUID caseId) {
```

- [ ] **Step 4: Annotate getContext method**

```java
  @GET
  @Path("/{caseId}/context")
  @Operation(summary = "Get full case context",
             description = "Returns the complete context data of a case instance")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @APIResponse(responseCode = "200", description = "Case context data")
  @APIResponse(responseCode = "404", description = "Case instance not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> getContext(@PathParam("caseId") UUID caseId) {
```

- [ ] **Step 5: Annotate getContextPath method**

```java
  @GET
  @Path("/{caseId}/context/{path}")
  @Operation(summary = "Get case context by path",
             description = "Returns a specific value from the case context using dot-notation path")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @Parameter(name = "path", description = "Dot-notation context path (e.g., customer.name)",
             required = true, example = "customer.name")
  @APIResponse(responseCode = "200", description = "Value at context path")
  @APIResponse(responseCode = "404", description = "Case instance not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> getContextPath(@PathParam("caseId") UUID caseId, @PathParam("path") String path) {
```

- [ ] **Step 6: Verify compilation and commit**

Run:
```bash
./mvnw compile -pl . -q
```

```bash
git add src/main/java/io/casehub/flow/rest/CaseInstanceResource.java
git commit -m "feat: add OpenAPI annotations to CaseInstanceResource

Refs #11"
```

---

### Task 5: Annotate CaseControlResource

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/CaseControlResource.java`

- [ ] **Step 1: Add OpenAPI imports and @Tag**

Add these imports to CaseControlResource.java:

```java
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
```

Add `@Tag` to the class:

```java
@Path("/api/v1/cases/{caseId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Case Control", description = "Case lifecycle operations (suspend, resume, cancel)")
public class CaseControlResource {
```

- [ ] **Step 2: Annotate suspend method**

Add before the existing `@POST` on `suspend`:

```java
  @POST
  @Path("suspend")
  @Operation(summary = "Suspend a running case",
             description = "Queues a case suspension for async processing")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @APIResponse(responseCode = "202", description = "Suspension queued",
               content = @Content(schema = @Schema(implementation = CaseControlResponse.class)))
  @APIResponse(responseCode = "404", description = "Case not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "409", description = "Invalid state transition",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> suspend(
```

- [ ] **Step 3: Annotate resume method**

```java
  @POST
  @Path("resume")
  @Operation(summary = "Resume a suspended case",
             description = "Queues a case resumption for async processing")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @APIResponse(responseCode = "202", description = "Resumption queued",
               content = @Content(schema = @Schema(implementation = CaseControlResponse.class)))
  @APIResponse(responseCode = "404", description = "Case not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "409", description = "Invalid state transition",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> resume(@PathParam("caseId") UUID caseId, CaseControlRequest request) {
```

- [ ] **Step 4: Annotate cancel method**

```java
  @POST
  @Path("cancel")
  @Operation(summary = "Cancel a running case",
             description = "Queues a case cancellation for async processing")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @APIResponse(responseCode = "202", description = "Cancellation queued",
               content = @Content(schema = @Schema(implementation = CaseControlResponse.class)))
  @APIResponse(responseCode = "404", description = "Case not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "409", description = "Invalid state transition",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> cancel(@PathParam("caseId") UUID caseId, CaseControlRequest request) {
```

- [ ] **Step 5: Verify compilation and commit**

Run:
```bash
./mvnw compile -pl . -q
```

```bash
git add src/main/java/io/casehub/flow/rest/CaseControlResource.java
git commit -m "feat: add OpenAPI annotations to CaseControlResource

Refs #11"
```

---

### Task 6: Annotate EventLogResource

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/EventLogResource.java`

- [ ] **Step 1: Add OpenAPI imports and @Tag**

Add these imports to EventLogResource.java:

```java
import io.casehub.flow.rest.dto.EventLogEntryResponse;
import io.casehub.flow.rest.dto.PagedResponse;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
```

Add `@Tag` to the class:

```java
@Path("/api/v1/cases/{caseId}/events")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Event Log", description = "Case event log and audit trail")
public class EventLogResource {
```

- [ ] **Step 2: Annotate getEventLog method**

Add before `@GET` on the `getEventLog` method:

```java
  @GET
  @Operation(summary = "Get case event log",
             description = "Returns a paginated and filtered event log for a case instance")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @Parameter(name = "page", description = "Page number (1-indexed)", example = "1")
  @Parameter(name = "size", description = "Page size (1-1000)", example = "50")
  @Parameter(name = "eventType", description = "Filter by event type (repeatable)",
             example = "CASE_STARTED")
  @Parameter(name = "streamType", description = "Filter by stream type (repeatable)",
             example = "CASE")
  @APIResponse(responseCode = "200", description = "Paginated event log",
               content = @Content(schema = @Schema(implementation = PagedResponse.class)))
  @APIResponse(responseCode = "400", description = "Invalid parameters",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "404", description = "Case instance not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> getEventLog(
```

- [ ] **Step 3: Verify compilation and commit**

Run:
```bash
./mvnw compile -pl . -q
```

```bash
git add src/main/java/io/casehub/flow/rest/EventLogResource.java
git commit -m "feat: add OpenAPI annotations to EventLogResource

Refs #11"
```

---

### Task 7: Annotate SignalResource

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/SignalResource.java`

- [ ] **Step 1: Add OpenAPI imports and @Tag**

Add these imports to SignalResource.java:

```java
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
```

Add `@Tag` to the class:

```java
@Path("/api/v1/cases/{caseId}/signals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Signals", description = "Send signals to running cases")
public class SignalResource {
```

- [ ] **Step 2: Annotate sendSignal method**

Add before `@POST` on the `sendSignal` method:

```java
  @POST
  @Operation(summary = "Send signal to a case",
             description = "Sends a signal value to a running case instance at the specified context path")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @RequestBody(description = "Signal with context path and value",
               required = true,
               content = @Content(schema = @Schema(implementation = SendSignalRequest.class)))
  @APIResponse(responseCode = "202", description = "Signal accepted",
               content = @Content(schema = @Schema(implementation = SignalResponse.class)))
  @APIResponse(responseCode = "400", description = "Invalid request",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "404", description = "Case not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> sendSignal(
```

- [ ] **Step 3: Verify compilation and commit**

Run:
```bash
./mvnw compile -pl . -q
```

```bash
git add src/main/java/io/casehub/flow/rest/SignalResource.java
git commit -m "feat: add OpenAPI annotations to SignalResource

Refs #11"
```

---

### Task 8: Write integration tests for OpenAPI spec validation

**Files:**
- Create: `src/test/java/io/casehub/flow/rest/OpenApiSpecIT.java`

- [ ] **Step 1: Write the integration test class**

Create `src/test/java/io/casehub/flow/rest/OpenApiSpecIT.java`:

```java
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
import java.util.List;
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

    Map<String, Object> spec = response.jsonPath().getMap("$");
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
```

- [ ] **Step 2: Run the integration tests**

Run:
```bash
./mvnw test -pl . -Dtest="OpenApiSpecIT"
```

Expected: all 6 tests pass.

- [ ] **Step 3: Run the full test suite**

Run:
```bash
./mvnw verify -pl .
```

Expected: all tests pass (existing + new), BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/OpenApiSpecIT.java
git commit -m "test: add integration tests for OpenAPI spec validation

Refs #11"
```
