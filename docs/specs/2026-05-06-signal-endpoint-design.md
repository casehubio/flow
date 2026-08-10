# Signal Endpoint REST API Design

**Date:** 2026-05-06  
**Issue:** #5 - Implement REST API v1 — signal endpoint for external events  
**Status:** Design Approved  
**Author:** Claude Code + Dmitrii Tikhomirov

## Overview

Implement REST API endpoint for sending external signals/events into running case instances. Signals update the CaseContext at a specified path, triggering worker execution and potential state transitions in casehub-engine.

## Requirements Summary

From issue #5:

**Endpoint:**
- `POST /api/v1/cases/{caseId}/signals` — send external signal to case instance

**Acceptance Criteria:**
- POST endpoint accepts path (location in CaseContext) and value (signal data)
- Signal is routed to casehub-engine's signal processing mechanism via `CaseHubRuntime.signal()`
- Returns 202 Accepted if signal queued successfully
- Returns 404 if caseId doesn't exist
- Returns 400 if signal payload validation fails (null path or value)
- Error responses follow RFC 7807 format
- Integration tests verify signal triggers worker execution and context updates

**Deferred (not in MVP):**
- Idempotency: duplicate signal deduplication (can be added later with Idempotency-Key header)
- Signal history/audit trail (covered by issue #7)

**Key Decisions:**
- **Async model:** 202 Accepted response (signal queued, not processed)
- **Direct mapping:** REST API `{ path, value }` maps directly to `CaseHubRuntime.signal(caseId, path, value)`
- **Minimal validation:** Null-checks only; rely on engine for semantic validation
- **Reactive error handling:** Catch exceptions from runtime, don't pre-validate case existence
- **Separate resource:** New `SignalResource` class for clean isolation (future: signal history)

## Architecture

### Component Layers

```
┌─────────────────────────────────────────┐
│  REST Layer                             │
│  SignalResource                         │
│  POST /api/v1/cases/{caseId}/signals    │
└─────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────┐
│  Runtime Layer                          │
│  CaseHubRuntime.signal(caseId, path, value)  │
└─────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────┐
│  casehub-engine                         │
│  - Updates CaseContext at path          │
│  - Triggers worker execution            │
│  - Processes state transitions          │
└─────────────────────────────────────────┘
```

**Design Rationale:**

- **Separate SignalResource**: Isolates signal functionality from CaseInstanceResource, allows future expansion (GET /signals for history)
- **No service layer**: `signal()` is a simple void method on runtime; adding a service would be over-engineering
- **Direct runtime injection**: Minimal indirection for straightforward operation

## Components

### 1. SignalResource

**Purpose:** JAX-RS endpoint for receiving signal requests and delegating to casehub-engine runtime.

**Location:** `src/main/java/io/casehub/flow/rest/SignalResource.java`

**Implementation:**

```java
package io.casehub.flow.rest;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.flow.exception.CaseNotFoundException;
import io.casehub.flow.rest.dto.SendSignalRequest;
import io.casehub.flow.rest.dto.SignalResponse;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * REST API for sending signals to case instances.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /api/v1/cases/{caseId}/signals — send signal to case
 * </ul>
 */
@Path("/api/v1/cases/{caseId}/signals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SignalResource {

  private static final Logger LOG = Logger.getLogger(SignalResource.class);

  @Inject CaseHubRuntime caseHubRuntime;

  /**
   * Send signal to case instance.
   *
   * @param caseId case instance UUID
   * @param request signal request with path and value
   * @return 202 Accepted if signal queued, 404 if case not found, 400 for invalid request
   */
  @POST
  public Uni<Response> sendSignal(
      @PathParam("caseId") UUID caseId,
      SendSignalRequest request) {

    // Validation
    if (request == null || request.path() == null || request.value() == null) {
      return Uni.createFrom()
          .item(
              Response.status(400)
                  .entity(
                      new ProblemDetail(
                          "Invalid request",
                          400,
                          "Request body, path, and value are required"))
                  .build());
    }

    // Send signal to engine
    return Uni.createFrom()
        .item(
            () -> {
              caseHubRuntime.signal(caseId, request.path(), request.value());
              return new SignalResponse(caseId, "accepted", "Signal queued for processing");
            })
        .map(response -> Response.status(202).entity(response).build())
        .onFailure(CaseNotFoundException.class)
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Case not found: %s", caseId);
              return Response.status(404)
                  .entity(new ProblemDetail("Case not found", 404, ex.getMessage()))
                  .build();
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOG.errorf(
                  ex, "Failed to send signal to case %s at path %s", caseId, request.path());
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to send signal: " + ex.getMessage()))
                  .build();
            });
  }

  /**
   * RFC 7807 Problem Details for HTTP APIs.
   *
   * @param title a short, human-readable summary of the problem type
   * @param status the HTTP status code
   * @param detail a human-readable explanation specific to this occurrence
   */
  public record ProblemDetail(String title, int status, String detail) {}
}
```

**Key Points:**

- `Uni.createFrom().item(() -> ...)` wraps the void `signal()` call
- Minimal validation: null-checks for request, path, and value
- Reactive error handling: catch exceptions from `caseHubRuntime.signal()`
- Logging at appropriate levels: WARN for 404, ERROR for 500
- RFC 7807 ProblemDetail for all error responses

### 2. SendSignalRequest (DTO)

**Purpose:** Request payload for signal endpoint.

**Location:** `src/main/java/io/casehub/flow/rest/dto/SendSignalRequest.java`

**Implementation:**

```java
package io.casehub.flow.rest.dto;

/**
 * Request payload for sending signal to case instance.
 *
 * @param path dot-notation path in CaseContext (e.g., "approvals.user", "orders[0].status")
 * @param value signal data to set at path
 */
public record SendSignalRequest(String path, Object value) {}
```

**Example Request:**

```json
POST /api/v1/cases/550e8400-e29b-41d4-a716-446655440000/signals
Content-Type: application/json

{
  "path": "approvals.user",
  "value": {
    "approved": true,
    "userId": "123",
    "timestamp": "2026-05-06T10:30:00Z"
  }
}
```

### 3. SignalResponse (DTO)

**Purpose:** Response payload for successful signal acceptance.

**Location:** `src/main/java/io/casehub/flow/rest/dto/SignalResponse.java`

**Implementation:**

```java
package io.casehub.flow.rest.dto;

import java.util.UUID;

/**
 * Response for signal acceptance.
 *
 * @param caseId case instance UUID
 * @param status acceptance status ("accepted")
 * @param message human-readable message
 */
public record SignalResponse(UUID caseId, String status, String message) {}
```

**Example Response (202 Accepted):**

```json
HTTP/1.1 202 Accepted
Content-Type: application/json

{
  "caseId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "accepted",
  "message": "Signal queued for processing"
}
```

## Error Handling

### Validation Errors (400 Bad Request)

**Triggers:**
- Request body is null
- `path` field is null
- `value` field is null

**Response:**

```json
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "title": "Invalid request",
  "status": 400,
  "detail": "Request body, path, and value are required"
}
```

### Case Not Found (404 Not Found)

**Triggers:**
- `CaseNotFoundException` thrown by `caseHubRuntime.signal()`

**Response:**

```json
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "title": "Case not found",
  "status": 404,
  "detail": "Case instance with UUID 550e8400-... not found"
}
```

**Logging:** WARN level with caseId

### Runtime Errors (500 Internal Server Error)

**Triggers:**
- Any other `RuntimeException` from `caseHubRuntime.signal()`

**Response:**

```json
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "title": "Internal server error",
  "status": 500,
  "detail": "Failed to send signal: <sanitized error message>"
}
```

**Logging:** ERROR level with full exception stack trace, caseId, and path

## Data Flow

```
1. Client sends POST /api/v1/cases/{caseId}/signals
   ↓
2. SignalResource validates request (null checks)
   ↓
3. SignalResource calls caseHubRuntime.signal(caseId, path, value)
   ↓
4. CaseHubRuntime updates CaseContext at path
   ↓
5. casehub-engine triggers worker execution (async)
   ↓
6. Workers process context change, update state
   ↓
7. SignalResource returns 202 Accepted immediately (before workers finish)
```

**Important:** The 202 response indicates signal acceptance, NOT completion of processing. Workers execute asynchronously after the HTTP response is returned.

## Testing Strategy

### Unit Tests (SignalResourceTest)

**Test Coverage:**

1. **Happy path** — 202 Accepted with valid request
   - Verify `caseHubRuntime.signal()` called with correct arguments
   - Verify response status and body

2. **Null path validation** — 400 Bad Request
   - Request with `path: null`
   - Verify error response

3. **Null value validation** — 400 Bad Request
   - Request with `value: null`
   - Verify error response

4. **Null request body** — 400 Bad Request
   - No request body
   - Verify error response

5. **Case not found** — 404 Not Found
   - Mock `CaseNotFoundException` from runtime
   - Verify error response and logging

6. **Runtime exception** — 500 Internal Server Error
   - Mock generic `RuntimeException` from runtime
   - Verify error response and logging

**Implementation:**

```java
@QuarkusTest
class SignalResourceTest {

  @InjectMock CaseHubRuntime caseHubRuntime;

  @Test
  void sendSignal_success_returns202() {
    UUID caseId = UUID.randomUUID();

    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "path": "approvals.user",
              "value": {"approved": true}
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", caseId)
        .then()
        .statusCode(202)
        .body("caseId", equalTo(caseId.toString()))
        .body("status", equalTo("accepted"))
        .body("message", containsString("queued"));

    verify(caseHubRuntime).signal(eq(caseId), eq("approvals.user"), any());
  }

  @Test
  void sendSignal_nullPath_returns400() {
    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "path": null,
              "value": {"approved": true}
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", UUID.randomUUID())
        .then()
        .statusCode(400)
        .body("title", equalTo("Invalid request"))
        .body("status", equalTo(400));
  }

  @Test
  void sendSignal_nullValue_returns400() {
    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "path": "approvals.user",
              "value": null
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", UUID.randomUUID())
        .then()
        .statusCode(400)
        .body("title", equalTo("Invalid request"));
  }

  @Test
  void sendSignal_caseNotFound_returns404() {
    UUID caseId = UUID.randomUUID();
    doThrow(new CaseNotFoundException(caseId))
        .when(caseHubRuntime)
        .signal(any(), any(), any());

    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "path": "test.path",
              "value": "test"
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", caseId)
        .then()
        .statusCode(404)
        .body("title", equalTo("Case not found"));
  }

  @Test
  void sendSignal_runtimeException_returns500() {
    doThrow(new RuntimeException("Database error"))
        .when(caseHubRuntime)
        .signal(any(), any(), any());

    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "path": "test.path",
              "value": "test"
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", UUID.randomUUID())
        .then()
        .statusCode(500)
        .body("title", equalTo("Internal server error"))
        .body("detail", containsString("Failed to send signal"));
  }
}
```

### Integration Tests (SignalResourceIT)

**Test Coverage:**

1. **End-to-end signal processing**
   - Start a real case instance
   - Send signal via REST API
   - Wait for worker execution (async)
   - Verify context updated via GET /context endpoint

2. **Signal triggers state transition**
   - Start case in initial state
   - Send signal that should trigger transition
   - Verify case moved to new state

**Implementation:**

```java
@QuarkusTest
class SignalResourceIT {

  @Inject CaseDefinitionService definitionService;
  @Inject CaseInstanceService caseInstanceService;

  @Test
  void sendSignal_updatesContextAndTriggersWorkers() {
    // 1. Start a test case
    UUID caseId = startTestCase();

    // 2. Send signal
    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "path": "approval.status",
              "value": "approved"
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", caseId)
        .then()
        .statusCode(202);

    // 3. Wait for async worker processing
    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> {
              // 4. Verify context updated
              String contextValue =
                  given()
                      .when()
                      .get("/api/v1/cases/{caseId}/context/approval.status", caseId)
                      .then()
                      .statusCode(200)
                      .extract()
                      .asString();

              assertThat(contextValue).isEqualTo("\"approved\"");
            });
  }

  @Test
  void sendSignal_nonExistentCase_returns404() {
    UUID nonExistentCaseId = UUID.randomUUID();

    given()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {
              "path": "test.path",
              "value": "test"
            }
            """)
        .when()
        .post("/api/v1/cases/{caseId}/signals", nonExistentCaseId)
        .then()
        .statusCode(404)
        .body("title", equalTo("Case not found"));
  }

  private UUID startTestCase() {
    // Helper method to start a test case instance
    // Implementation depends on test case definition setup
  }
}
```

## Future Enhancements

### 1. Idempotency (Deferred)

**Approach:** Client-side idempotency key

**Implementation:**
```java
@POST
public Uni<Response> sendSignal(
    @PathParam("caseId") UUID caseId,
    @HeaderParam("Idempotency-Key") String idempotencyKey,
    SendSignalRequest request) {
  
  if (idempotencyKey != null) {
    // Check idempotency table
    return checkIdempotency(caseId, idempotencyKey)
        .flatMap(isDuplicate -> {
          if (isDuplicate) {
            return Uni.createFrom().item(202 Accepted);
          }
          // Store idempotency key, send signal
          return storeAndSendSignal(caseId, idempotencyKey, request);
        });
  }
  
  // No idempotency key, send signal directly
  return sendSignalDirect(caseId, request);
}
```

**Database Schema:**
```sql
CREATE TABLE signal_idempotency (
  case_id UUID NOT NULL,
  idempotency_key VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  PRIMARY KEY (case_id, idempotency_key)
);

CREATE INDEX idx_signal_idempotency_created_at 
  ON signal_idempotency(created_at);

-- Cleanup job: DELETE WHERE created_at < NOW() - INTERVAL '24 hours'
```

### 2. Signal History (Issue #7)

**Endpoint:**
```
GET /api/v1/cases/{caseId}/signals
```

**Response:**
```json
{
  "signals": [
    {
      "path": "approvals.user",
      "value": {"approved": true},
      "timestamp": "2026-05-06T10:30:00Z",
      "source": "external-api"
    }
  ]
}
```

**Implementation:** Query casehub-engine ledger/event log

### 3. Observability

**Metrics (Micrometer/Prometheus):**
- `signals_sent_total` — counter, labels: {caseId, path, status}
- `signals_errors_total` — counter, labels: {errorType}
- `signal_processing_duration_seconds` — histogram

**Tracing (OpenTelemetry):**
- Span: `POST /api/v1/cases/{caseId}/signals`
- Attributes: caseId, path, responseStatus

**Logging:**
- Already included: WARN for 404, ERROR for 500
- Future: structured logging with correlation IDs

### 4. Security (Future)

- **Authentication:** JWT/OAuth2 integration
- **Authorization:** Case-level permissions (who can send signals to which cases)
- **Rate Limiting:** Prevent signal flooding/abuse
- **Input Sanitization:** Additional validation for path format, value size limits

### 5. casehub-engine DLQ Integration

From issue #5 notes:
> Check if casehub-engine has built-in signal deduplication (DLQ module from #194, #193)

**Action:** Investigate casehubio/engine issues #194, #193 to understand:
- Does DLQ provide signal deduplication?
- Is it automatic or opt-in?
- What configuration is needed?

**If DLQ provides deduplication:** Document behavior in API docs, no need to implement in REST layer

**If DLQ doesn't provide deduplication:** Implement idempotency as described above

## Implementation Checklist

- [ ] Create `SignalResource.java`
- [ ] Create `SendSignalRequest.java` DTO
- [ ] Create `SignalResponse.java` DTO
- [ ] Add `CaseNotFoundException` if not already exists
- [ ] Write unit tests (`SignalResourceTest`)
- [ ] Write integration tests (`SignalResourceIT`)
- [ ] Update API documentation / OpenAPI spec (issue #11)
- [ ] Verify error logging works correctly
- [ ] Manual testing: send signals via curl/Postman
- [ ] Update README with signal endpoint examples

## Open Questions

None — all questions resolved during design phase.

## References

- Issue #5: https://github.com/casehubio/flow/issues/5
- Epic #1: https://github.com/casehubio/flow/issues/1
- casehub-engine API: `CaseHubRuntime.signal(UUID caseId, String path, Object value)`
- Previous design: `2026-05-05-case-lifecycle-api-design.md`
