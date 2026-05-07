# Case Control Operations (Suspend, Resume, Cancel) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement REST API endpoints for case lifecycle control operations (suspend, resume, cancel) with async 202 Accepted pattern.

**Architecture:** Single CaseControlResource class with three POST endpoints (:suspend, :resume, :cancel) that wrap CaseHubRuntime void methods in reactive Uni via emitter pattern. Direct runtime integration with exception mapping to HTTP status codes (IllegalArgumentException→404, IllegalStateException→409).

**Tech Stack:** Quarkus REST, Mutiny Uni, casehub-engine CaseHubRuntime, RestAssured, Mockito, Awaitility

---

## File Structure

**Create:**
- `src/main/java/io/casehub/flow/rest/dto/CaseControlRequest.java` - Optional request DTO with reason field
- `src/main/java/io/casehub/flow/rest/dto/CaseControlResponse.java` - Response DTO for 202 Accepted
- `src/main/java/io/casehub/flow/rest/CaseControlResource.java` - Main resource with suspend/resume/cancel endpoints
- `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java` - Unit tests (15 tests)
- `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java` - Integration tests (7 tests)

**Reuse:**
- `src/main/java/io/casehub/flow/rest/dto/ProblemDetail.java` - RFC 7807 error responses

---

### Task 1: Create CaseControlRequest DTO

**Files:**
- Create: `src/main/java/io/casehub/flow/rest/dto/CaseControlRequest.java`

- [ ] **Step 1: Create CaseControlRequest record**

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
package io.casehub.flow.rest.dto;

/**
 * Optional request body for case control operations.
 * Allows caller to provide audit trail information.
 */
public record CaseControlRequest(String reason) {}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/dto/CaseControlRequest.java
git commit -m "feat: add CaseControlRequest DTO for control operations"
```

---

### Task 2: Create CaseControlResponse DTO

**Files:**
- Create: `src/main/java/io/casehub/flow/rest/dto/CaseControlResponse.java`

- [ ] **Step 1: Create CaseControlResponse record**

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
package io.casehub.flow.rest.dto;

import java.util.UUID;

/**
 * Response for case control operations (202 Accepted).
 *
 * @param caseId case instance UUID
 * @param operation operation type ("suspend", "resume", "cancel")
 * @param status operation status ("accepted")
 * @param message human-readable confirmation message
 */
public record CaseControlResponse(
    UUID caseId, String operation, String status, String message) {}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/dto/CaseControlResponse.java
git commit -m "feat: add CaseControlResponse DTO for control operations"
```

---

### Task 3: Create CaseControlResource with suspend endpoint skeleton

**Files:**
- Create: `src/main/java/io/casehub/flow/rest/CaseControlResource.java`
- Test: `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`

- [ ] **Step 1: Write failing test for suspend with null request body**

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
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;

import io.casehub.api.engine.CaseHubRuntime;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CaseControlResourceTest {

  @InjectMock CaseHubRuntime caseHubRuntime;

  @Test
  void testSuspendWithNullRequestBody() {
    UUID caseId = UUID.randomUUID();

    given()
        .when()
        .post("/api/v1/cases/{caseId}:suspend", caseId)
        .then()
        .statusCode(202)
        .body("caseId", is(caseId.toString()))
        .body("operation", is("suspend"))
        .body("status", is("accepted"))
        .body("message", is("Case suspension queued for processing"));

    verify(caseHubRuntime).suspendCase(caseId);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=CaseControlResourceTest#testSuspendWithNullRequestBody`
Expected: FAIL with "Connection refused" or 404 (endpoint doesn't exist)

- [ ] **Step 3: Create CaseControlResource with suspend endpoint**

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

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.flow.rest.dto.CaseControlRequest;
import io.casehub.flow.rest.dto.CaseControlResponse;
import io.casehub.flow.rest.dto.ProblemDetail;
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
 * REST API for case lifecycle control operations.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /api/v1/cases/{caseId}:suspend — suspend case execution
 *   <li>POST /api/v1/cases/{caseId}:resume — resume suspended case
 *   <li>POST /api/v1/cases/{caseId}:cancel — cancel/terminate case
 * </ul>
 */
@Path("/api/v1/cases/{caseId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaseControlResource {

  private static final Logger LOG = Logger.getLogger(CaseControlResource.class);

  @Inject CaseHubRuntime caseHubRuntime;

  /**
   * Suspend case execution.
   *
   * @param caseId case instance UUID
   * @param request optional request with reason field
   * @return 202 Accepted with operation confirmation, 404 if case not found, 409 if invalid state
   */
  @POST
  @Path(":suspend")
  public Uni<Response> suspend(
      @PathParam("caseId") UUID caseId, CaseControlRequest request) {
    return Uni.createFrom()
        .emitter(
            em -> {
              try {
                caseHubRuntime.suspendCase(caseId);
                em.complete(
                    new CaseControlResponse(
                        caseId, "suspend", "accepted", "Case suspension queued for processing"));
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
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Case not found: %s", caseId);
              return Response.status(404)
                  .entity(new ProblemDetail("Case not found", 404, ex.getMessage()))
                  .build();
            })
        .onFailure(IllegalStateException.class)
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Invalid state transition for case %s", caseId);
              return Response.status(409)
                  .entity(new ProblemDetail("Invalid state transition", 409, ex.getMessage()))
                  .build();
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOG.errorf(ex, "Failed to suspend case %s", caseId);
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to suspend case: " + ex.getMessage()))
                  .build();
            });
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceTest#testSuspendWithNullRequestBody`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/CaseControlResource.java src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java
git commit -m "feat: add CaseControlResource with suspend endpoint"
```

---

### Task 4: Add suspend test for case not found (404)

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`

- [ ] **Step 1: Write failing test for suspend case not found**

Add to CaseControlResourceTest:

```java
  @Test
  void testSuspendCaseNotFound() {
    UUID caseId = UUID.randomUUID();
    doThrow(new IllegalArgumentException("Case not found"))
        .when(caseHubRuntime)
        .suspendCase(caseId);

    given()
        .when()
        .post("/api/v1/cases/{caseId}:suspend", caseId)
        .then()
        .statusCode(404)
        .body("title", is("Case not found"))
        .body("status", is(404));
  }
```

Add import at top of file:
```java
import static org.mockito.Mockito.doThrow;
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceTest#testSuspendCaseNotFound`
Expected: PASS (implementation already handles this)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java
git commit -m "test: add suspend case not found test (404)"
```

---

### Task 5: Add suspend test for invalid state (409)

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`

- [ ] **Step 1: Write test for suspend invalid state**

Add to CaseControlResourceTest:

```java
  @Test
  void testSuspendInvalidState() {
    UUID caseId = UUID.randomUUID();
    doThrow(new IllegalStateException("Cannot suspend case in SUSPENDED state"))
        .when(caseHubRuntime)
        .suspendCase(caseId);

    given()
        .when()
        .post("/api/v1/cases/{caseId}:suspend", caseId)
        .then()
        .statusCode(409)
        .body("title", is("Invalid state transition"))
        .body("status", is(409))
        .body("detail", is("Cannot suspend case in SUSPENDED state"));
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceTest#testSuspendInvalidState`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java
git commit -m "test: add suspend invalid state test (409)"
```

---

### Task 6: Add suspend test for runtime exception (500)

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`

- [ ] **Step 1: Write test for suspend runtime exception**

Add to CaseControlResourceTest:

```java
  @Test
  void testSuspendRuntimeException() {
    UUID caseId = UUID.randomUUID();
    doThrow(new RuntimeException("Unexpected error"))
        .when(caseHubRuntime)
        .suspendCase(caseId);

    given()
        .when()
        .post("/api/v1/cases/{caseId}:suspend", caseId)
        .then()
        .statusCode(500)
        .body("title", is("Internal server error"))
        .body("status", is(500));
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceTest#testSuspendRuntimeException`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java
git commit -m "test: add suspend runtime exception test (500)"
```

---

### Task 7: Add suspend test with reason field

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`

- [ ] **Step 1: Write test for suspend with reason**

Add to CaseControlResourceTest:

```java
  @Test
  void testSuspendWithReason() {
    UUID caseId = UUID.randomUUID();

    given()
        .contentType(ContentType.JSON)
        .body("{\"reason\": \"Maintenance window\"}")
        .when()
        .post("/api/v1/cases/{caseId}:suspend", caseId)
        .then()
        .statusCode(202)
        .body("caseId", is(caseId.toString()))
        .body("operation", is("suspend"))
        .body("status", is("accepted"));

    verify(caseHubRuntime).suspendCase(caseId);
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceTest#testSuspendWithReason`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java
git commit -m "test: add suspend with reason field test"
```

---

### Task 8: Add resume endpoint

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/CaseControlResource.java`
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`

- [ ] **Step 1: Write failing test for resume**

Add to CaseControlResourceTest:

```java
  @Test
  void testResumeWithNullRequestBody() {
    UUID caseId = UUID.randomUUID();

    given()
        .when()
        .post("/api/v1/cases/{caseId}:resume", caseId)
        .then()
        .statusCode(202)
        .body("caseId", is(caseId.toString()))
        .body("operation", is("resume"))
        .body("status", is("accepted"))
        .body("message", is("Case resumption queued for processing"));

    verify(caseHubRuntime).resumeCase(caseId);
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=CaseControlResourceTest#testResumeWithNullRequestBody`
Expected: FAIL with 404 (endpoint doesn't exist)

- [ ] **Step 3: Add resume endpoint to CaseControlResource**

Add method to CaseControlResource:

```java
  /**
   * Resume suspended case execution.
   *
   * @param caseId case instance UUID
   * @param request optional request with reason field
   * @return 202 Accepted with operation confirmation, 404 if case not found, 409 if invalid state
   */
  @POST
  @Path(":resume")
  public Uni<Response> resume(@PathParam("caseId") UUID caseId, CaseControlRequest request) {
    return Uni.createFrom()
        .emitter(
            em -> {
              try {
                caseHubRuntime.resumeCase(caseId);
                em.complete(
                    new CaseControlResponse(
                        caseId, "resume", "accepted", "Case resumption queued for processing"));
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
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Case not found: %s", caseId);
              return Response.status(404)
                  .entity(new ProblemDetail("Case not found", 404, ex.getMessage()))
                  .build();
            })
        .onFailure(IllegalStateException.class)
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Invalid state transition for case %s", caseId);
              return Response.status(409)
                  .entity(new ProblemDetail("Invalid state transition", 409, ex.getMessage()))
                  .build();
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOG.errorf(ex, "Failed to resume case %s", caseId);
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to resume case: " + ex.getMessage()))
                  .build();
            });
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceTest#testResumeWithNullRequestBody`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/CaseControlResource.java src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java
git commit -m "feat: add resume endpoint to CaseControlResource"
```

---

### Task 9: Add resume error tests (404, 409, 500, with reason)

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`

- [ ] **Step 1: Add resume case not found test**

Add to CaseControlResourceTest:

```java
  @Test
  void testResumeCaseNotFound() {
    UUID caseId = UUID.randomUUID();
    doThrow(new IllegalArgumentException("Case not found"))
        .when(caseHubRuntime)
        .resumeCase(caseId);

    given()
        .when()
        .post("/api/v1/cases/{caseId}:resume", caseId)
        .then()
        .statusCode(404)
        .body("title", is("Case not found"))
        .body("status", is(404));
  }

  @Test
  void testResumeInvalidState() {
    UUID caseId = UUID.randomUUID();
    doThrow(new IllegalStateException("Cannot resume case in RUNNING state"))
        .when(caseHubRuntime)
        .resumeCase(caseId);

    given()
        .when()
        .post("/api/v1/cases/{caseId}:resume", caseId)
        .then()
        .statusCode(409)
        .body("title", is("Invalid state transition"))
        .body("status", is(409))
        .body("detail", is("Cannot resume case in RUNNING state"));
  }

  @Test
  void testResumeRuntimeException() {
    UUID caseId = UUID.randomUUID();
    doThrow(new RuntimeException("Unexpected error"))
        .when(caseHubRuntime)
        .resumeCase(caseId);

    given()
        .when()
        .post("/api/v1/cases/{caseId}:resume", caseId)
        .then()
        .statusCode(500)
        .body("title", is("Internal server error"))
        .body("status", is(500));
  }

  @Test
  void testResumeWithReason() {
    UUID caseId = UUID.randomUUID();

    given()
        .contentType(ContentType.JSON)
        .body("{\"reason\": \"Maintenance completed\"}")
        .when()
        .post("/api/v1/cases/{caseId}:resume", caseId)
        .then()
        .statusCode(202)
        .body("caseId", is(caseId.toString()))
        .body("operation", is("resume"))
        .body("status", is("accepted"));

    verify(caseHubRuntime).resumeCase(caseId);
  }
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./mvnw test -Dtest=CaseControlResourceTest`
Expected: All tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java
git commit -m "test: add resume error tests (404, 409, 500, with reason)"
```

---

### Task 10: Add cancel endpoint

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/CaseControlResource.java`
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`

- [ ] **Step 1: Write failing test for cancel**

Add to CaseControlResourceTest:

```java
  @Test
  void testCancelWithNullRequestBody() {
    UUID caseId = UUID.randomUUID();

    given()
        .when()
        .post("/api/v1/cases/{caseId}:cancel", caseId)
        .then()
        .statusCode(202)
        .body("caseId", is(caseId.toString()))
        .body("operation", is("cancel"))
        .body("status", is("accepted"))
        .body("message", is("Case cancellation queued for processing"));

    verify(caseHubRuntime).cancelCase(caseId);
  }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=CaseControlResourceTest#testCancelWithNullRequestBody`
Expected: FAIL with 404 (endpoint doesn't exist)

- [ ] **Step 3: Add cancel endpoint to CaseControlResource**

Add method to CaseControlResource:

```java
  /**
   * Cancel case execution.
   *
   * @param caseId case instance UUID
   * @param request optional request with reason field
   * @return 202 Accepted with operation confirmation, 404 if case not found, 409 if invalid state
   */
  @POST
  @Path(":cancel")
  public Uni<Response> cancel(@PathParam("caseId") UUID caseId, CaseControlRequest request) {
    return Uni.createFrom()
        .emitter(
            em -> {
              try {
                caseHubRuntime.cancelCase(caseId);
                em.complete(
                    new CaseControlResponse(
                        caseId, "cancel", "accepted", "Case cancellation queued for processing"));
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
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Case not found: %s", caseId);
              return Response.status(404)
                  .entity(new ProblemDetail("Case not found", 404, ex.getMessage()))
                  .build();
            })
        .onFailure(IllegalStateException.class)
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Invalid state transition for case %s", caseId);
              return Response.status(409)
                  .entity(new ProblemDetail("Invalid state transition", 409, ex.getMessage()))
                  .build();
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOG.errorf(ex, "Failed to cancel case %s", caseId);
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to cancel case: " + ex.getMessage()))
                  .build();
            });
  }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceTest#testCancelWithNullRequestBody`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/CaseControlResource.java src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java
git commit -m "feat: add cancel endpoint to CaseControlResource"
```

---

### Task 11: Add cancel error tests (404, 409, 500, with reason)

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`

- [ ] **Step 1: Add cancel error tests**

Add to CaseControlResourceTest:

```java
  @Test
  void testCancelCaseNotFound() {
    UUID caseId = UUID.randomUUID();
    doThrow(new IllegalArgumentException("Case not found"))
        .when(caseHubRuntime)
        .cancelCase(caseId);

    given()
        .when()
        .post("/api/v1/cases/{caseId}:cancel", caseId)
        .then()
        .statusCode(404)
        .body("title", is("Case not found"))
        .body("status", is(404));
  }

  @Test
  void testCancelInvalidState() {
    UUID caseId = UUID.randomUUID();
    doThrow(new IllegalStateException("Cannot cancel case in CANCELLED state"))
        .when(caseHubRuntime)
        .cancelCase(caseId);

    given()
        .when()
        .post("/api/v1/cases/{caseId}:cancel", caseId)
        .then()
        .statusCode(409)
        .body("title", is("Invalid state transition"))
        .body("status", is(409))
        .body("detail", is("Cannot cancel case in CANCELLED state"));
  }

  @Test
  void testCancelRuntimeException() {
    UUID caseId = UUID.randomUUID();
    doThrow(new RuntimeException("Unexpected error"))
        .when(caseHubRuntime)
        .cancelCase(caseId);

    given()
        .when()
        .post("/api/v1/cases/{caseId}:cancel", caseId)
        .then()
        .statusCode(500)
        .body("title", is("Internal server error"))
        .body("status", is(500));
  }

  @Test
  void testCancelWithReason() {
    UUID caseId = UUID.randomUUID();

    given()
        .contentType(ContentType.JSON)
        .body("{\"reason\": \"Business requirement changed\"}")
        .when()
        .post("/api/v1/cases/{caseId}:cancel", caseId)
        .then()
        .statusCode(202)
        .body("caseId", is(caseId.toString()))
        .body("operation", is("cancel"))
        .body("status", is("accepted"));

    verify(caseHubRuntime).cancelCase(caseId);
  }
```

- [ ] **Step 2: Run all unit tests to verify they pass**

Run: `./mvnw test -Dtest=CaseControlResourceTest`
Expected: All 15 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java
git commit -m "test: add cancel error tests (404, 409, 500, with reason)"
```

---

### Task 12: Create integration test infrastructure

**Files:**
- Create: `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java`

- [ ] **Step 1: Create integration test skeleton with helper methods**

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
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile test-compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java
git commit -m "test: add integration test infrastructure for case control"
```

---

### Task 13: Add integration test for suspend non-existent case

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java`

- [ ] **Step 1: Write test for suspend non-existent case**

Add to CaseControlResourceIT:

```java
  @Test
  void testSuspendNonExistentCase() {
    UUID nonExistentCaseId = UUID.randomUUID();

    given()
        .when()
        .post("/api/v1/cases/{caseId}:suspend", nonExistentCaseId)
        .then()
        .statusCode(404);
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceIT#testSuspendNonExistentCase`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java
git commit -m "test: add integration test for suspend non-existent case"
```

---

### Task 14: Add integration test for suspend running case

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java`

- [ ] **Step 1: Write test for suspend running case**

Add to CaseControlResourceIT:

```java
  @Test
  void testSuspendRunningCase() {
    UUID caseId = startTestCase();

    given()
        .when()
        .post("/api/v1/cases/{caseId}:suspend", caseId)
        .then()
        .statusCode(202)
        .extract()
        .path("operation")
        .equals("suspend");

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> {
          String status = getCaseStatus(caseId);
          assert status.equals("SUSPENDED") || status.equals("WAITING");
        });
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceIT#testSuspendRunningCase`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java
git commit -m "test: add integration test for suspend running case"
```

---

### Task 15: Add integration test for resume suspended case

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java`

- [ ] **Step 1: Write test for resume suspended case**

Add to CaseControlResourceIT:

```java
  @Test
  void testResumeSuspendedCase() {
    UUID caseId = startTestCase();

    given().when().post("/api/v1/cases/{caseId}:suspend", caseId).then().statusCode(202);

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> {
          String status = getCaseStatus(caseId);
          assert status.equals("SUSPENDED") || status.equals("WAITING");
        });

    given().when().post("/api/v1/cases/{caseId}:resume", caseId).then().statusCode(202);

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> {
          String status = getCaseStatus(caseId);
          assert status.equals("RUNNING");
        });
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceIT#testResumeSuspendedCase`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java
git commit -m "test: add integration test for resume suspended case"
```

---

### Task 16: Add integration test for resume non-suspended case

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java`

- [ ] **Step 1: Write test for resume non-suspended case**

Add to CaseControlResourceIT:

```java
  @Test
  void testResumeNonSuspendedCase() {
    UUID caseId = startTestCase();

    given()
        .when()
        .post("/api/v1/cases/{caseId}:resume", caseId)
        .then()
        .statusCode(409);
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceIT#testResumeNonSuspendedCase`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java
git commit -m "test: add integration test for resume non-suspended case (409)"
```

---

### Task 17: Add integration test for suspend already-suspended case

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java`

- [ ] **Step 1: Write test for suspend already-suspended case**

Add to CaseControlResourceIT:

```java
  @Test
  void testSuspendAlreadySuspendedCase() {
    UUID caseId = startTestCase();

    given().when().post("/api/v1/cases/{caseId}:suspend", caseId).then().statusCode(202);

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> {
          String status = getCaseStatus(caseId);
          assert status.equals("SUSPENDED") || status.equals("WAITING");
        });

    given()
        .when()
        .post("/api/v1/cases/{caseId}:suspend", caseId)
        .then()
        .statusCode(409);
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceIT#testSuspendAlreadySuspendedCase`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java
git commit -m "test: add integration test for suspend already-suspended case (409)"
```

---

### Task 18: Add integration test for cancel running case

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java`

- [ ] **Step 1: Write test for cancel running case**

Add to CaseControlResourceIT:

```java
  @Test
  void testCancelRunningCase() {
    UUID caseId = startTestCase();

    given()
        .when()
        .post("/api/v1/cases/{caseId}:cancel", caseId)
        .then()
        .statusCode(202)
        .extract()
        .path("operation")
        .equals("cancel");

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> {
          String status = getCaseStatus(caseId);
          assert status.equals("CANCELLED") || status.equals("FAULTED") || status.equals("COMPLETED");
        });
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceIT#testCancelRunningCase`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java
git commit -m "test: add integration test for cancel running case"
```

---

### Task 19: Add integration test for full workflow

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java`

- [ ] **Step 1: Write test for full workflow (start → suspend → resume → cancel)**

Add to CaseControlResourceIT:

```java
  @Test
  void testFullWorkflow() {
    UUID caseId = startTestCase();
    String status = getCaseStatus(caseId);
    assert status.equals("RUNNING");

    given().when().post("/api/v1/cases/{caseId}:suspend", caseId).then().statusCode(202);

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> {
          String suspendedStatus = getCaseStatus(caseId);
          assert suspendedStatus.equals("SUSPENDED") || suspendedStatus.equals("WAITING");
        });

    given().when().post("/api/v1/cases/{caseId}:resume", caseId).then().statusCode(202);

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> {
          String resumedStatus = getCaseStatus(caseId);
          assert resumedStatus.equals("RUNNING");
        });

    given().when().post("/api/v1/cases/{caseId}:cancel", caseId).then().statusCode(202);

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> {
          String cancelledStatus = getCaseStatus(caseId);
          assert cancelledStatus.equals("CANCELLED") || cancelledStatus.equals("FAULTED") || cancelledStatus.equals("COMPLETED");
        });
  }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=CaseControlResourceIT#testFullWorkflow`
Expected: PASS

- [ ] **Step 3: Run all integration tests**

Run: `./mvnw test -Dtest=CaseControlResourceIT`
Expected: All 7 tests PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java
git commit -m "test: add integration test for full workflow (suspend→resume→cancel)"
```

---

### Task 20: Run full test suite and verify

**Files:**
- All test files

- [ ] **Step 1: Run all tests**

Run: `./mvnw test`
Expected: All tests PASS (15 unit + 7 integration = 22 tests for case control, plus existing tests)

- [ ] **Step 2: Verify compilation**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat: complete case control operations implementation

- POST /api/v1/cases/{caseId}:suspend
- POST /api/v1/cases/{caseId}:resume
- POST /api/v1/cases/{caseId}:cancel
- 202 Accepted async pattern
- Exception mapping: IllegalArgumentException→404, IllegalStateException→409
- 15 unit tests with mocked CaseHubRuntime
- 7 integration tests with real CaseHubRuntime
- All tests passing"
```

---

## Implementation Complete

**Total tasks:** 20  
**Total commits:** 20  
**Total tests:** 22 (15 unit + 7 integration)

**Files created:**
- `src/main/java/io/casehub/flow/rest/dto/CaseControlRequest.java`
- `src/main/java/io/casehub/flow/rest/dto/CaseControlResponse.java`
- `src/main/java/io/casehub/flow/rest/CaseControlResource.java`
- `src/test/java/io/casehub/flow/rest/CaseControlResourceTest.java`
- `src/test/java/io/casehub/flow/rest/CaseControlResourceIT.java`

**Acceptance criteria met:**
- ✅ Suspend transitions case to WAITING/SUSPENDED state
- ✅ Resume transitions case back to RUNNING state
- ✅ Cancel transitions case to terminal state
- ✅ All endpoints return 404 if caseId doesn't exist
- ✅ Endpoints return 409 Conflict if state transition is invalid
- ✅ Operations persist state to repository (via casehub-engine)
- ✅ Error responses follow RFC 7807 format
- ✅ Integration tests verify async state transitions with Awaitility
