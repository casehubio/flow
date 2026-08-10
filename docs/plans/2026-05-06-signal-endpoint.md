# Signal Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement POST /api/v1/cases/{caseId}/signals endpoint for sending external signals to case instances

**Architecture:** New SignalResource JAX-RS endpoint that validates requests and delegates to CaseHubRuntime.signal(). Returns 202 Accepted on success, 404 for missing cases, 400 for validation errors. No service layer - direct runtime injection.

**Tech Stack:** Quarkus REST, Mutiny (reactive), casehub-engine CaseHubRuntime, JUnit 5, RestAssured, Mockito

---

## File Structure

**New files to create:**
- `src/main/java/io/casehub/flow/rest/dto/SendSignalRequest.java` — Request DTO for signal payload
- `src/main/java/io/casehub/flow/rest/dto/SignalResponse.java` — Response DTO for 202 Accepted
- `src/main/java/io/casehub/flow/rest/SignalResource.java` — JAX-RS endpoint
- `src/test/java/io/casehub/flow/rest/SignalResourceTest.java` — Unit tests with mocked runtime
- `src/test/java/io/casehub/flow/rest/SignalResourceIT.java` — Integration tests with real runtime

**Existing files to reference:**
- `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java` — Pattern for error handling, ProblemDetail
- `src/main/java/io/casehub/flow/exception/CaseInstanceNotFoundException.java` — Exception for 404 errors
- `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java` — Test pattern reference

**Note on exceptions:** The design spec references `CaseNotFoundException`, but the codebase uses `CaseInstanceNotFoundException`. We'll use the existing `CaseInstanceNotFoundException` for consistency.

---

## Task 1: Create SendSignalRequest DTO

**Files:**
- Create: `src/main/java/io/casehub/flow/rest/dto/SendSignalRequest.java`

- [ ] **Step 1: Create SendSignalRequest record**

Create file with copyright header and record definition:

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
 * Request payload for sending signal to case instance.
 *
 * @param path dot-notation path in CaseContext (e.g., "approvals.user", "orders[0].status")
 * @param value signal data to set at path
 */
public record SendSignalRequest(String path, Object value) {}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -DskipTests`

Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/dto/SendSignalRequest.java
git commit -m "feat: add SendSignalRequest DTO for signal endpoint"
```

---

## Task 2: Create SignalResponse DTO

**Files:**
- Create: `src/main/java/io/casehub/flow/rest/dto/SignalResponse.java`

- [ ] **Step 1: Create SignalResponse record**

Create file with copyright header and record definition:

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
 * Response for signal acceptance.
 *
 * @param caseId case instance UUID
 * @param status acceptance status ("accepted")
 * @param message human-readable message
 */
public record SignalResponse(UUID caseId, String status, String message) {}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -DskipTests`

Expected: SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/dto/SignalResponse.java
git commit -m "feat: add SignalResponse DTO for signal endpoint"
```

---

## Task 3: Create SignalResource skeleton with validation test

**Files:**
- Create: `src/main/java/io/casehub/flow/rest/SignalResource.java`
- Create: `src/test/java/io/casehub/flow/rest/SignalResourceTest.java`

- [ ] **Step 1: Write test for null request body validation**

Create test file:

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
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SignalResourceTest {

  @Test
  void sendSignal_nullRequestBody_returns400() {
    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/api/v1/cases/{caseId}/signals", UUID.randomUUID())
        .then()
        .statusCode(400)
        .body("title", equalTo("Invalid request"))
        .body("status", equalTo(400));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SignalResourceTest#sendSignal_nullRequestBody_returns400`

Expected: FAIL - endpoint not found (404)

- [ ] **Step 3: Create minimal SignalResource**

Create resource file:

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

import io.casehub.flow.rest.dto.SendSignalRequest;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

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

  @POST
  public Uni<Response> sendSignal(
      @PathParam("caseId") UUID caseId, SendSignalRequest request) {

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

    return Uni.createFrom().item(Response.status(202).build());
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

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SignalResourceTest#sendSignal_nullRequestBody_returns400`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/SignalResource.java src/test/java/io/casehub/flow/rest/SignalResourceTest.java
git commit -m "feat: add SignalResource with null request validation"
```

---

## Task 4: Add null path validation

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/SignalResourceTest.java`

- [ ] **Step 1: Write test for null path**

Add test method to SignalResourceTest:

```java
@Test
void sendSignal_nullPath_returns400() {
  given()
      .contentType(ContentType.JSON)
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
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=SignalResourceTest#sendSignal_nullPath_returns400`

Expected: PASS (already handled by existing validation)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/SignalResourceTest.java
git commit -m "test: add null path validation test"
```

---

## Task 5: Add null value validation

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/SignalResourceTest.java`

- [ ] **Step 1: Write test for null value**

Add test method to SignalResourceTest:

```java
@Test
void sendSignal_nullValue_returns400() {
  given()
      .contentType(ContentType.JSON)
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
```

- [ ] **Step 2: Run test to verify it passes**

Run: `./mvnw test -Dtest=SignalResourceTest#sendSignal_nullValue_returns400`

Expected: PASS (already handled by existing validation)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/SignalResourceTest.java
git commit -m "test: add null value validation test"
```

---

## Task 6: Add CaseHubRuntime injection and happy path test

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/SignalResource.java`
- Modify: `src/test/java/io/casehub/flow/rest/SignalResourceTest.java`

- [ ] **Step 1: Write happy path test with mocked runtime**

Add imports and mock setup to SignalResourceTest:

```java
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.casehub.api.engine.CaseHubRuntime;
import io.quarkus.test.InjectMock;
import static org.hamcrest.Matchers.containsString;
```

Add test method:

```java
@InjectMock CaseHubRuntime caseHubRuntime;

@Test
void sendSignal_validRequest_returns202() {
  UUID caseId = UUID.randomUUID();

  given()
      .contentType(ContentType.JSON)
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SignalResourceTest#sendSignal_validRequest_returns202`

Expected: FAIL - response body doesn't match (empty 202 response)

- [ ] **Step 3: Inject CaseHubRuntime and implement signal call**

Modify SignalResource:

```java
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.flow.rest.dto.SignalResponse;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
```

Add fields and update sendSignal method:

```java
private static final Logger LOG = Logger.getLogger(SignalResource.class);

@Inject CaseHubRuntime caseHubRuntime;

@POST
public Uni<Response> sendSignal(
    @PathParam("caseId") UUID caseId, SendSignalRequest request) {

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
      .map(response -> Response.status(202).entity(response).build());
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SignalResourceTest#sendSignal_validRequest_returns202`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/SignalResource.java src/test/java/io/casehub/flow/rest/SignalResourceTest.java
git commit -m "feat: add CaseHubRuntime integration and signal delivery"
```

---

## Task 7: Add case not found error handling

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/SignalResource.java`
- Modify: `src/test/java/io/casehub/flow/rest/SignalResourceTest.java`

- [ ] **Step 1: Write test for case not found**

Add imports to SignalResourceTest:

```java
import static org.mockito.Mockito.doThrow;
import io.casehub.flow.exception.CaseInstanceNotFoundException;
```

Add test method:

```java
@Test
void sendSignal_caseNotFound_returns404() {
  UUID caseId = UUID.randomUUID();
  doThrow(new CaseInstanceNotFoundException(caseId))
      .when(caseHubRuntime)
      .signal(any(), any(), any());

  given()
      .contentType(ContentType.JSON)
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SignalResourceTest#sendSignal_caseNotFound_returns404`

Expected: FAIL - no error handling, returns 500 or exception

- [ ] **Step 3: Add error handling for CaseInstanceNotFoundException**

Modify SignalResource sendSignal method to add error recovery:

```java
import io.casehub.flow.exception.CaseInstanceNotFoundException;
```

Update the return statement in sendSignal:

```java
// Send signal to engine
return Uni.createFrom()
    .item(
        () -> {
          caseHubRuntime.signal(caseId, request.path(), request.value());
          return new SignalResponse(caseId, "accepted", "Signal queued for processing");
        })
    .map(response -> Response.status(202).entity(response).build())
    .onFailure(CaseInstanceNotFoundException.class)
    .recoverWithItem(
        ex -> {
          LOG.warnf(ex, "Case not found: %s", caseId);
          return Response.status(404)
              .entity(new ProblemDetail("Case not found", 404, ex.getMessage()))
              .build();
        });
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SignalResourceTest#sendSignal_caseNotFound_returns404`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/SignalResource.java src/test/java/io/casehub/flow/rest/SignalResourceTest.java
git commit -m "feat: add 404 error handling for non-existent cases"
```

---

## Task 8: Add generic runtime error handling

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/SignalResource.java`
- Modify: `src/test/java/io/casehub/flow/rest/SignalResourceTest.java`

- [ ] **Step 1: Write test for runtime exception**

Add test method to SignalResourceTest:

```java
@Test
void sendSignal_runtimeException_returns500() {
  doThrow(new RuntimeException("Database error"))
      .when(caseHubRuntime)
      .signal(any(), any(), any());

  given()
      .contentType(ContentType.JSON)
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=SignalResourceTest#sendSignal_runtimeException_returns500`

Expected: FAIL - no generic error handling

- [ ] **Step 3: Add generic error recovery**

Modify SignalResource sendSignal method to add final error handler:

```java
// Send signal to engine
return Uni.createFrom()
    .item(
        () -> {
          caseHubRuntime.signal(caseId, request.path(), request.value());
          return new SignalResponse(caseId, "accepted", "Signal queued for processing");
        })
    .map(response -> Response.status(202).entity(response).build())
    .onFailure(CaseInstanceNotFoundException.class)
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=SignalResourceTest#sendSignal_runtimeException_returns500`

Expected: PASS

- [ ] **Step 5: Run all unit tests**

Run: `./mvnw test -Dtest=SignalResourceTest`

Expected: All tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/casehub/flow/rest/SignalResource.java src/test/java/io/casehub/flow/rest/SignalResourceTest.java
git commit -m "feat: add 500 error handling for runtime exceptions"
```

---

## Task 9: Add integration test for end-to-end signal processing

**Files:**
- Create: `src/test/java/io/casehub/flow/rest/SignalResourceIT.java`

- [ ] **Step 1: Create integration test skeleton**

Create test file:

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
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SignalResourceIT {

  @Test
  void sendSignal_nonExistentCase_returns404() {
    UUID nonExistentCaseId = UUID.randomUUID();

    given()
        .contentType(ContentType.JSON)
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

  @Test
  void sendSignal_updatesContextAndTriggersWorkers() {
    // 1. Start a test case
    UUID caseId = startTestCase();

    // 2. Send signal
    given()
        .contentType(ContentType.JSON)
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

  private UUID startTestCase() {
    Map<String, Object> request =
        Map.of(
            "definition",
            Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
            "context",
            Map.of("documentId", "DOC-123", "submittedBy", "alice@example.com"));

    String response =
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/api/v1/cases")
            .then()
            .statusCode(200)
            .extract()
            .path("caseId");

    return UUID.fromString(response);
  }
}
```

- [ ] **Step 2: Run integration tests**

Run: `./mvnw verify -Dit.test=SignalResourceIT`

Expected: Tests PASS (verifies end-to-end signal delivery and context update)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/SignalResourceIT.java
git commit -m "test: add integration tests for signal endpoint"
```

---

## Task 10: Run full test suite and verify

**Files:**
- N/A (verification task)

- [ ] **Step 1: Run all tests**

Run: `./mvnw verify`

Expected: All tests PASS

- [ ] **Step 2: Verify compilation without warnings**

Run: `./mvnw clean compile`

Expected: BUILD SUCCESS with no warnings

- [ ] **Step 3: Manual smoke test with curl**

Start dev mode:
```bash
./mvnw quarkus:dev
```

In another terminal, start a case and send a signal:

```bash
# Start a case
CASE_ID=$(curl -s -X POST http://localhost:8080/api/v1/cases \
  -H "Content-Type: application/json" \
  -d '{
    "definition": {
      "namespace": "test-api",
      "name": "Document Approval",
      "version": "1.0.0"
    },
    "context": {
      "documentId": "DOC-999"
    }
  }' | jq -r '.caseId')

echo "Started case: $CASE_ID"

# Send signal
curl -X POST http://localhost:8080/api/v1/cases/$CASE_ID/signals \
  -H "Content-Type: application/json" \
  -d '{
    "path": "approval.status",
    "value": "approved"
  }'

# Check context
curl -s http://localhost:8080/api/v1/cases/$CASE_ID/context/approval.status
```

Expected:
- Signal returns 202 with `{"caseId":"...","status":"accepted","message":"Signal queued for processing"}`
- Context endpoint returns `"approved"`

- [ ] **Step 4: Test error cases manually**

Test 404:
```bash
curl -X POST http://localhost:8080/api/v1/cases/00000000-0000-0000-0000-000000000000/signals \
  -H "Content-Type: application/json" \
  -d '{"path": "test", "value": "test"}'
```

Expected: 404 with RFC 7807 format

Test 400:
```bash
curl -X POST http://localhost:8080/api/v1/cases/$CASE_ID/signals \
  -H "Content-Type: application/json" \
  -d '{"path": null, "value": "test"}'
```

Expected: 400 with "Invalid request"

Stop dev mode: `Ctrl+C`

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "feat: complete signal endpoint implementation

Implements POST /api/v1/cases/{caseId}/signals endpoint for sending
external signals to case instances (issue #5).

Features:
- Accepts {path, value} payload
- Returns 202 Accepted on success
- 404 for missing cases, 400 for validation errors
- RFC 7807 error format
- Full unit and integration test coverage

Manual testing verified with curl."
```

---

## Self-Review Checklist

**Spec coverage:**
- ✅ POST /api/v1/cases/{caseId}/signals endpoint
- ✅ Accepts path and value in request body
- ✅ Returns 202 Accepted on success
- ✅ Returns 404 if caseId doesn't exist
- ✅ Returns 400 for validation errors (null path/value)
- ✅ RFC 7807 error format
- ✅ Unit tests with mocked runtime
- ✅ Integration tests with real runtime
- ✅ Logging at appropriate levels

**Placeholders:** None - all code is complete

**Type consistency:**
- SendSignalRequest(String path, Object value) - used consistently
- SignalResponse(UUID caseId, String status, String message) - used consistently
- CaseHubRuntime.signal(UUID caseId, String path, Object value) - matches engine API

**Missing from spec:** None - all requirements covered

---

## Execution Notes

**Estimated time:** 45-60 minutes for full implementation

**Dependencies:**
- Requires existing CaseInstanceResource for ProblemDetail pattern reference
- Requires existing test infrastructure (QuarkusTest, RestAssured)
- Requires casehub-engine with CaseHubRuntime.signal() method

**Known issues:** None anticipated

**Testing strategy:**
- Unit tests use @InjectMock for CaseHubRuntime
- Integration tests use real runtime with test case definitions
- Manual testing verifies full workflow

**Future work (not in this plan):**
- Idempotency support (Idempotency-Key header)
- Signal history endpoint (issue #7)
- Metrics/observability (Micrometer)
- OpenAPI spec update (issue #11)
