# OpenAPI Specification and Swagger UI Design

**Issue:** #11 (part of epic #1)
**Date:** 2026-05-29
**Status:** Approved

## Goal

Add OpenAPI 3.0 spec generation and Swagger UI to document all REST API v1 endpoints, using MicroProfile OpenAPI annotations as the single source of truth.

## Architecture

### Dependency

Add `quarkus-smallrye-openapi` to `pom.xml` (version managed by the Quarkus BOM).

### Endpoints

| Endpoint | Purpose | Availability |
|---|---|---|
| `/q/openapi` | OpenAPI 3.0 spec (JSON/YAML) | All profiles |
| `/q/swagger-ui` | Interactive Swagger UI | Dev/test only |

### Approach

Annotations-first: all OpenAPI metadata lives in code via MicroProfile OpenAPI annotations. SmallRye generates the spec automatically at build time and serves it at `/q/openapi`.

## Configuration

In `application.properties`:

```properties
# OpenAPI metadata
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

## Resource Annotations

Each resource gets `@Tag` for grouping. Each method gets `@Operation`, `@APIResponse`, `@Parameter`.

### Tags

| Resource | Tag name | Description |
|---|---|---|
| CaseDefinitionResource | Case Definitions | Query registered case definitions |
| CaseInstanceResource | Case Instances | Case instance lifecycle and context |
| CaseControlResource | Case Control | Case lifecycle operations (suspend, resume, cancel) |
| EventLogResource | Event Log | Case event log and audit trail |
| SignalResource | Signals | Send signals to running cases |

### Annotation Pattern

```java
@Tag(name = "Case Control", description = "Case lifecycle operations (suspend, resume, cancel)")
@Path("/api/v1/cases/{caseId}")
public class CaseControlResource {

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
    public Uni<Response> suspend(...) { ... }
}
```

### Endpoint Inventory (11 total)

**CaseDefinitionResource** (`/api/v1/case-definitions`):

| Method | Path | Summary | Success | Errors |
|---|---|---|---|---|
| GET | `/` | List all case definitions | 200 | 400 |
| GET | `/{namespace}/{name}` | Get definitions by namespace and name | 200 | 404 |
| GET | `/{namespace}/{name}/{version}` | Get definition by key | 200 | 404 |

**CaseInstanceResource** (`/api/v1/cases`):

| Method | Path | Summary | Success | Errors |
|---|---|---|---|---|
| POST | `/` | Start a new case instance | 200 | 400, 404, 500 |
| GET | `/{caseId}` | Get case instance by ID | 200 | 404, 500 |
| GET | `/{caseId}/context` | Get full case context | 200 | 404, 500 |
| GET | `/{caseId}/context/{path}` | Get case context by path | 200 | 404, 500 |

**CaseControlResource** (`/api/v1/cases/{caseId}`):

| Method | Path | Summary | Success | Errors |
|---|---|---|---|---|
| POST | `suspend` | Suspend a running case | 202 | 404, 409, 500 |
| POST | `resume` | Resume a suspended case | 202 | 404, 409, 500 |
| POST | `cancel` | Cancel a running case | 202 | 404, 409, 500 |

**EventLogResource** (`/api/v1/cases/{caseId}/events`):

| Method | Path | Summary | Success | Errors |
|---|---|---|---|---|
| GET | `/` | Get case event log | 200 | 400, 404, 500 |

**SignalResource** (`/api/v1/cases/{caseId}/signals`):

| Method | Path | Summary | Success | Errors |
|---|---|---|---|---|
| POST | `/` | Send signal to a case | 202 | 400, 404, 500 |

## DTO Annotations

All 9 DTOs are Java records. Each field gets `@Schema(description, example)`.

### StartCaseRequest

```java
@Schema(description = "Request to start a new case instance")
public record StartCaseRequest(
    @Schema(description = "Case definition reference", required = true)
    @NotNull @Valid CaseDefinitionRef definition,

    @Schema(description = "Initial case context data", nullable = true,
            example = "{\"customer\": {\"name\": \"John\"}}")
    Map<String, Object> context
) {
    @Schema(description = "Reference to a registered case definition")
    public record CaseDefinitionRef(
        @Schema(description = "Case namespace", required = true, example = "acme")
        @NotBlank String namespace,
        @Schema(description = "Case name", required = true, example = "Order Processing")
        @NotBlank String name,
        @Schema(description = "Case version", required = true, example = "1.0.0")
        @NotBlank String version
    ) {}
}
```

### ProblemDetail (RFC 7807)

```java
@Schema(description = "RFC 7807 Problem Details error response")
public record ProblemDetail(
    @Schema(description = "Short human-readable error summary", example = "Case not found")
    String title,
    @Schema(description = "HTTP status code", example = "404")
    int status,
    @Schema(description = "Detailed human-readable explanation",
            example = "Case instance abc-123 not found")
    String detail
) {}
```

### CaseInstanceResponse

```java
@Schema(description = "Case instance status and metadata")
public record CaseInstanceResponse(
    @Schema(description = "Case instance UUID", required = true,
            example = "550e8400-e29b-41d4-a716-446655440000")
    UUID caseId,
    @Schema(description = "Current case status", required = true, example = "RUNNING")
    CaseStatus status,
    @Schema(description = "Case namespace", required = true, example = "acme")
    String namespace,
    @Schema(description = "Case name", required = true, example = "Order Processing")
    String name,
    @Schema(description = "Case version", required = true, example = "1.0.0")
    String version,
    @Schema(description = "Case creation timestamp", required = true)
    Instant createdAt,
    @Schema(description = "Last update timestamp", required = true)
    Instant updatedAt
) {}
```

### CaseControlRequest

```java
@Schema(description = "Request body for case control operations")
public record CaseControlRequest(
    @Schema(description = "Optional reason for the operation", nullable = true,
            example = "Maintenance window")
    String reason
) {}
```

### CaseControlResponse

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
    String message
) {}
```

### SendSignalRequest

```java
@Schema(description = "Request to send a signal to a running case")
public record SendSignalRequest(
    @Schema(description = "Dot-notation context path", required = true,
            example = "approvals.manager")
    @NotBlank String path,
    @Schema(description = "Signal value (String, Number, Boolean, Map, or List)",
            required = true, example = "approved")
    @NotNull Object value
) {}
```

### SignalResponse

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
    String message
) {}
```

### EventLogEntryResponse

```java
@Schema(description = "Single event log entry from case audit trail")
public record EventLogEntryResponse(
    @Schema(description = "Type of case event", required = true, example = "CASE_STARTED")
    CaseHubEventType eventType,
    @Schema(description = "Event stream type", required = true, example = "CASE")
    EventStreamType streamType,
    @Schema(description = "Event timestamp", required = true)
    Instant timestamp,
    @Schema(description = "Event payload data", nullable = true)
    JsonNode payload,
    @Schema(description = "Event metadata", nullable = true)
    JsonNode metadata
) {}
```

### PagedResponse

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
    int totalPages
) {}
```

## Testing Strategy

### Integration test (`OpenApiSpecIT.java`)

Extends `CaseHubIntegrationTestBase`. Fetches `/q/openapi?format=JSON`, parses the response and validates:

**API info:**
- title = "CaseHub Flow API"
- version = "1.0.0"

**All 11 paths present:**
- `/api/v1/case-definitions` (GET)
- `/api/v1/case-definitions/{namespace}/{name}` (GET)
- `/api/v1/case-definitions/{namespace}/{name}/{version}` (GET)
- `/api/v1/cases` (POST)
- `/api/v1/cases/{caseId}` (GET)
- `/api/v1/cases/{caseId}/context` (GET)
- `/api/v1/cases/{caseId}/context/{path}` (GET)
- `/api/v1/cases/{caseId}/suspend` (POST)
- `/api/v1/cases/{caseId}/resume` (POST)
- `/api/v1/cases/{caseId}/cancel` (POST)
- `/api/v1/cases/{caseId}/signals` (POST)

**Response codes per endpoint:**
- Each endpoint has documented success response (200 or 202)
- Error responses (400, 404, 409, 500) documented where applicable

**Schema components present:**
- ProblemDetail, StartCaseRequest, CaseInstanceResponse, CaseControlRequest, CaseControlResponse, SendSignalRequest, SignalResponse, EventLogEntryResponse, PagedResponse

**Swagger UI:**
- `GET /q/swagger-ui` returns 200 (HTML page)

## Files Changed

| File | Change |
|---|---|
| `pom.xml` | Add `quarkus-smallrye-openapi` dependency |
| `src/main/resources/application.properties` | Add OpenAPI metadata + Swagger UI config |
| `src/main/java/io/casehub/flow/rest/CaseDefinitionResource.java` | Add @Tag, @Operation, @APIResponse, @Parameter |
| `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java` | Add @Tag, @Operation, @APIResponse, @Parameter |
| `src/main/java/io/casehub/flow/rest/CaseControlResource.java` | Add @Tag, @Operation, @APIResponse, @Parameter |
| `src/main/java/io/casehub/flow/rest/EventLogResource.java` | Add @Tag, @Operation, @APIResponse, @Parameter |
| `src/main/java/io/casehub/flow/rest/SignalResource.java` | Add @Tag, @Operation, @APIResponse, @Parameter |
| `src/main/java/io/casehub/flow/rest/dto/StartCaseRequest.java` | Add @Schema on class + fields |
| `src/main/java/io/casehub/flow/rest/dto/CaseInstanceResponse.java` | Add @Schema on class + fields |
| `src/main/java/io/casehub/flow/rest/dto/CaseControlRequest.java` | Add @Schema on class + fields |
| `src/main/java/io/casehub/flow/rest/dto/CaseControlResponse.java` | Add @Schema on class + fields |
| `src/main/java/io/casehub/flow/rest/dto/SendSignalRequest.java` | Add @Schema on class + fields |
| `src/main/java/io/casehub/flow/rest/dto/SignalResponse.java` | Add @Schema on class + fields |
| `src/main/java/io/casehub/flow/rest/dto/EventLogEntryResponse.java` | Add @Schema on class + fields |
| `src/main/java/io/casehub/flow/rest/dto/PagedResponse.java` | Add @Schema on class + fields |
| `src/main/java/io/casehub/flow/rest/dto/ProblemDetail.java` | Add @Schema on class + fields |
| `src/test/java/io/casehub/flow/rest/OpenApiSpecIT.java` | New: integration tests for OpenAPI spec validation |
