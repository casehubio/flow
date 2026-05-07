# Case Control Operations (Suspend, Resume, Cancel) Design Specification

**Issue:** #6  
**Date:** 2026-05-07  
**Status:** Approved

## Goal

Implement REST API endpoints for case lifecycle control operations (suspend, resume, cancel) that integrate with casehub-engine's CaseHubRuntime methods.

## Architecture

### Resource Structure

**Single Resource Class:**
- `CaseControlResource` at `/api/v1/cases/{caseId}` with three POST operations:
  - `POST /api/v1/cases/{caseId}:suspend` - suspend case execution
  - `POST /api/v1/cases/{caseId}:resume` - resume suspended case
  - `POST /api/v1/cases/{caseId}:cancel` - cancel/terminate case

### Async 202 Accepted Pattern

All operations return **202 Accepted** immediately:
- Response confirms operation was queued
- Actual state change happens asynchronously
- Client polls `GET /api/v1/cases/{caseId}` to verify state transition
- Matches modern async API design patterns

### Direct CaseHubRuntime Integration

- Inject `CaseHubRuntime` directly (no service layer needed)
- Wrap void methods in reactive Uni via `Uni.createFrom().emitter()`
- Operations execute in background with proper error handling
- Exception mapping:
  - `IllegalArgumentException` → 404 Not Found (case doesn't exist)
  - `IllegalStateException` → 409 Conflict (invalid state transition)
  - `RuntimeException` → 500 Internal Server Error

## Implementation Details

### CaseControlResource Class

```java
package io.casehub.flow.rest;

@Path("/api/v1/cases/{caseId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaseControlResource {
  
  @Inject CaseHubRuntime caseHubRuntime;
  private static final Logger LOG = Logger.getLogger(CaseControlResource.class);
  
  @POST
  @Path(":suspend")
  public Uni<Response> suspend(
      @PathParam("caseId") UUID caseId, 
      CaseControlRequest request) {
    // Implementation
  }
  
  @POST
  @Path(":resume")
  public Uni<Response> resume(
      @PathParam("caseId") UUID caseId, 
      CaseControlRequest request) {
    // Implementation
  }
  
  @POST
  @Path(":cancel")
  public Uni<Response> cancel(
      @PathParam("caseId") UUID caseId, 
      CaseControlRequest request) {
    // Implementation
  }
}
```

### Reactive Execution Pattern

Each endpoint follows this flow:

1. Accept optional request body (null is valid)
2. Return 202 Accepted immediately
3. Execute operation in background via `Uni.createFrom().emitter()`
4. Map exceptions to appropriate HTTP status codes

**Implementation pattern:**
```java
return Uni.createFrom().emitter(em -> {
  try {
    caseHubRuntime.suspendCase(caseId);
    em.complete(new CaseControlResponse(
      caseId, 
      "suspend", 
      "accepted", 
      "Case suspension queued for processing"
    ));
  } catch (IllegalArgumentException e) {
    em.fail(e);
  } catch (IllegalStateException e) {
    em.fail(e);
  } catch (Exception e) {
    em.fail(e);
  }
})
.map(response -> Response.status(202).entity(response).build())
.onFailure(IllegalArgumentException.class)
  .recoverWithItem(ex -> {
    LOG.warnf(ex, "Case not found: %s", caseId);
    return Response.status(404)
      .entity(new ProblemDetail("Case not found", 404, ex.getMessage()))
      .build();
  })
.onFailure(IllegalStateException.class)
  .recoverWithItem(ex -> {
    LOG.warnf(ex, "Invalid state transition for case %s", caseId);
    return Response.status(409)
      .entity(new ProblemDetail("Invalid state transition", 409, ex.getMessage()))
      .build();
  })
.onFailure()
  .recoverWithItem(ex -> {
    LOG.errorf(ex, "Failed to suspend case %s", caseId);
    return Response.status(500)
      .entity(new ProblemDetail("Internal server error", 500, 
        "Failed to suspend case: " + ex.getMessage()))
      .build();
  });
```

### Exception Handling

**Try-catch in reactive emitter:**
- Wrap CaseHubRuntime method calls in try-catch
- Propagate exceptions via `emitter.fail(exception)`
- Handle in reactive chain with `.onFailure()` handlers

**HTTP Status Mapping:**
- `IllegalArgumentException` (case not found) → **404 Not Found**
- `IllegalStateException` (invalid state) → **409 Conflict**
- Any other `RuntimeException` → **500 Internal Server Error**

**Logging:**
- WARN level: 404 (case not found), 409 (invalid state transition)
- ERROR level: 500 (unexpected failures)
- Include caseId and operation name in all log messages

## DTOs

### Request DTO

```java
package io.casehub.flow.rest.dto;

/**
 * Optional request body for case control operations.
 * Allows caller to provide audit trail information.
 */
public record CaseControlRequest(String reason) {}
```

**Characteristics:**
- No Bean Validation annotations (all fields optional)
- Null request body is acceptable
- Reason field used for audit trail and logging

### Response DTO

```java
package io.casehub.flow.rest.dto;

import java.util.UUID;

/**
 * Response for case control operations (202 Accepted).
 */
public record CaseControlResponse(
  UUID caseId,
  String operation,  // "suspend", "resume", "cancel"
  String status,     // "accepted"
  String message     // Human-readable confirmation
) {}
```

**Example responses:**

Suspend:
```json
{
  "caseId": "123e4567-e89b-12d3-a456-426614174000",
  "operation": "suspend",
  "status": "accepted",
  "message": "Case suspension queued for processing"
}
```

Resume:
```json
{
  "caseId": "123e4567-e89b-12d3-a456-426614174000",
  "operation": "resume",
  "status": "accepted",
  "message": "Case resumption queued for processing"
}
```

Cancel:
```json
{
  "caseId": "123e4567-e89b-12d3-a456-426614174000",
  "operation": "cancel",
  "status": "accepted",
  "message": "Case cancellation queued for processing"
}
```

## Error Responses (RFC 7807)

Reuse existing `ProblemDetail(String title, int status, String detail)` from dto package.

### 404 Not Found

```json
{
  "title": "Case not found",
  "status": 404,
  "detail": "Case with ID 123e4567-e89b-12d3-a456-426614174000 not found"
}
```

### 409 Conflict (Invalid State Transition)

```json
{
  "title": "Invalid state transition",
  "status": 409,
  "detail": "Cannot suspend case in SUSPENDED state"
}
```

### 500 Internal Server Error

```json
{
  "title": "Internal server error",
  "status": 500,
  "detail": "Failed to suspend case: Unexpected runtime error"
}
```

## Testing Strategy

### Unit Tests (CaseControlResourceTest)

**Test infrastructure:**
- Use `@QuarkusTest` with `@InjectMock CaseHubRuntime`
- RestAssured for HTTP testing
- Mockito for mock configuration and verification

**Coverage per operation (suspend, resume, cancel):**

1. **Valid operation with null request body** → 202 Accepted
   - Verify response status 202
   - Verify CaseControlResponse fields
   - Verify CaseHubRuntime method called once with correct caseId

2. **Valid operation with reason in request** → 202 Accepted
   - Same as above
   - Verify reason is logged/processed

3. **Case not found** → 404 Not Found
   - Mock throws IllegalArgumentException("Case ... not found")
   - Verify 404 response
   - Verify ProblemDetail format
   - Verify WARN log

4. **Invalid state transition** → 409 Conflict
   - Mock throws IllegalStateException("Cannot suspend case in SUSPENDED state")
   - Verify 409 response
   - Verify ProblemDetail format
   - Verify WARN log

5. **Runtime exception** → 500 Internal Server Error
   - Mock throws RuntimeException("Unexpected error")
   - Verify 500 response
   - Verify ProblemDetail format
   - Verify ERROR log

**Total unit tests:** 15 (5 scenarios × 3 operations)

### Integration Tests (CaseControlResourceIT)

**Test infrastructure:**
- Use `@QuarkusTest` with real CaseHubRuntime
- RestAssured for HTTP calls
- Awaitility for async state verification
- Start real case instances for testing

**Test scenarios:**

1. **Suspend running case → verify SUSPENDED state**
   - Start case, verify RUNNING
   - POST :suspend, verify 202
   - Poll GET /cases/{caseId}, wait for SUSPENDED state
   - Use Awaitility with atMost(5, SECONDS)

2. **Resume suspended case → verify RUNNING state**
   - Start case, suspend it
   - POST :resume, verify 202
   - Poll GET /cases/{caseId}, wait for RUNNING state

3. **Cancel running case → verify terminal state**
   - Start case
   - POST :cancel, verify 202
   - Poll GET /cases/{caseId}, wait for CANCELLED/FAULTED state

4. **Suspend non-existent case → 404**
   - POST :suspend with random UUID
   - Verify 404 Not Found immediately

5. **Resume non-suspended case → 409**
   - Start case (RUNNING state)
   - POST :resume without suspending first
   - Verify 409 Conflict

6. **Suspend already-suspended case → 409**
   - Start case, suspend it
   - POST :suspend again
   - Verify 409 Conflict

7. **Full workflow: start → suspend → resume → cancel**
   - Verify each state transition
   - Verify all operations return 202
   - Verify final state is terminal

**Total integration tests:** 7

### Test Patterns

**Unit test structure (following SignalResourceTest):**
```java
@QuarkusTest
class CaseControlResourceTest {
  
  @InjectMock
  CaseHubRuntime caseHubRuntime;
  
  @Test
  void testSuspendValidCase() {
    UUID caseId = UUID.randomUUID();
    
    given()
      .contentType(ContentType.JSON)
      .body("{}")
      .when()
      .post("/api/v1/cases/{caseId}:suspend", caseId)
      .then()
      .statusCode(202)
      .body("caseId", is(caseId.toString()))
      .body("operation", is("suspend"))
      .body("status", is("accepted"));
    
    verify(caseHubRuntime).suspendCase(caseId);
  }
  
  @Test
  void testSuspendCaseNotFound() {
    UUID caseId = UUID.randomUUID();
    doThrow(new IllegalArgumentException("Case not found"))
      .when(caseHubRuntime).suspendCase(caseId);
    
    given()
      .contentType(ContentType.JSON)
      .when()
      .post("/api/v1/cases/{caseId}:suspend", caseId)
      .then()
      .statusCode(404)
      .body("title", is("Case not found"))
      .body("status", is(404));
  }
}
```

**Integration test structure (following SignalResourceIT):**
```java
@QuarkusTest
class CaseControlResourceIT {
  
  @Inject
  CaseHubRuntime caseHubRuntime;
  
  @Test
  void testSuspendAndResumeWorkflow() {
    // Start case
    UUID caseId = startTestCase();
    
    // Suspend
    given()
      .when()
      .post("/api/v1/cases/{caseId}:suspend", caseId)
      .then()
      .statusCode(202);
    
    // Verify SUSPENDED state
    await().atMost(5, SECONDS).until(() -> 
      getCaseStatus(caseId).equals("SUSPENDED")
    );
    
    // Resume
    given()
      .when()
      .post("/api/v1/cases/{caseId}:resume", caseId)
      .then()
      .statusCode(202);
    
    // Verify RUNNING state
    await().atMost(5, SECONDS).until(() -> 
      getCaseStatus(caseId).equals("RUNNING")
    );
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
```

## Dependencies

**No new dependencies required:**
- Quarkus REST (already present)
- Mutiny (already present)
- casehub-engine with CaseHubRuntime (already present)
- quarkus-junit-mockito (already added in signal endpoint work)
- RestAssured (already present)
- Awaitility (already used in SignalResourceIT)

## Files to Create/Modify

**Create:**
- `src/main/java/io/casehub/flow/rest/CaseControlResource.java`
- `src/main/java/io/casehub/flow/rest/dto/CaseControlRequest.java`
- `src/main/java/io/casehub/flow/rest/dto/CaseControlResponse.java`
- `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`
- `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java`

**Reuse (no modification):**
- `src/main/java/io/casehub/flow/rest/dto/ProblemDetail.java` (error responses)

## Acceptance Criteria Mapping

- ✅ Suspend transitions case to WAITING/SUSPENDED state (via CaseHubRuntime.suspendCase())
- ✅ Resume transitions case back to RUNNING state (via CaseHubRuntime.resumeCase())
- ✅ Cancel transitions case to terminal state (via CaseHubRuntime.cancelCase())
- ✅ All endpoints return 404 if caseId doesn't exist (IllegalArgumentException → 404)
- ✅ Endpoints return 409 Conflict if state transition is invalid (IllegalStateException → 409)
- ✅ Operations persist state to repository (casehub-engine handles this)
- ✅ Error responses follow RFC 7807 format (ProblemDetail DTO)
- ✅ Integration tests verify async state transitions (Awaitility with GET polling)

## Future Enhancements

**Out of scope for this implementation:**

1. **Bulk operations** - Suspend/resume/cancel multiple cases in one request
2. **Scheduled operations** - Schedule suspend/cancel at specific time
3. **Audit trail** - Persist reason field to event log (depends on issue #7)
4. **Idempotency keys** - Prevent duplicate operations via client-provided keys
5. **Webhooks** - Notify external systems when state changes
6. **Rollback** - Undo recent cancel operation

These can be added later based on production requirements.
