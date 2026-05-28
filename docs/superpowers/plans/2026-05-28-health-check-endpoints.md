# Health Check Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add k8s liveness and readiness health check endpoints using Quarkus SmallRye Health, with a custom readiness check for case engine initialization.

**Architecture:** Add `quarkus-smallrye-health` dependency for automatic `/q/health/live` and `/q/health/ready` endpoints. Liveness and database readiness are auto-configured. One custom `@Readiness` class (`CaseEngineHealthCheck`) observes `StartupEvent` to track engine initialization.

**Tech Stack:** Quarkus 3.32.2, SmallRye Health, MicroProfile Health API, JUnit 5, REST-assured, Testcontainers

**Spec:** `specs/2026-05-28-health-check-endpoints-design.md`
**Issue:** #10 (epic #1)

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `pom.xml` | Modify | Add `quarkus-smallrye-health` dependency |
| `src/main/java/io/casehub/flow/health/CaseEngineHealthCheck.java` | Create | Custom readiness check for case engine initialization |
| `src/test/java/io/casehub/flow/health/CaseEngineHealthCheckTest.java` | Create | Unit tests for CaseEngineHealthCheck |
| `src/test/java/io/casehub/flow/rest/HealthEndpointIT.java` | Create | Integration tests for health endpoints |

---

### Task 1: Add SmallRye Health dependency

**Files:**
- Modify: `pom.xml` (add dependency after the `quarkus-hibernate-validator` block, around line 84)

- [ ] **Step 1: Add the dependency to pom.xml**

Add the following dependency after `quarkus-hibernate-validator` (line 84) and before `jandex` (line 86):

```xml
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-smallrye-health</artifactId>
        </dependency>
```

No version needed — managed by the Quarkus BOM (`quarkus-bom` imported in `<dependencyManagement>`).

- [ ] **Step 2: Verify the dependency resolves**

Run:
```bash
./mvnw dependency:resolve -pl . -q
```

Expected: exits 0 with no errors.

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "feat: add quarkus-smallrye-health dependency

Refs #10"
```

---

### Task 2: Write unit tests for CaseEngineHealthCheck

**Files:**
- Create: `src/test/java/io/casehub/flow/health/CaseEngineHealthCheckTest.java`

- [ ] **Step 1: Write the unit test class**

Create `src/test/java/io/casehub/flow/health/CaseEngineHealthCheckTest.java`:

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
package io.casehub.flow.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CaseEngineHealthCheckTest {

  private CaseEngineHealthCheck healthCheck;

  @BeforeEach
  void setUp() {
    healthCheck = new CaseEngineHealthCheck();
  }

  @Test
  void returnsDownBeforeStartup() {
    HealthCheckResponse response = healthCheck.call();

    assertThat(response.getName()).isEqualTo("Case engine");
    assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
  }

  @Test
  void returnsUpAfterStartup() {
    healthCheck.onStartup(new StartupEvent());

    HealthCheckResponse response = healthCheck.call();

    assertThat(response.getName()).isEqualTo("Case engine");
    assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
  }
}
```

These are plain unit tests (no `@QuarkusTest`) — they instantiate the class directly and call `onStartup()` to simulate the CDI event.

- [ ] **Step 2: Run the tests — verify they fail**

Run:
```bash
./mvnw test -pl . -Dtest="CaseEngineHealthCheckTest" -q
```

Expected: compilation failure — `CaseEngineHealthCheck` does not exist yet.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/casehub/flow/health/CaseEngineHealthCheckTest.java
git commit -m "test: add unit tests for CaseEngineHealthCheck

Refs #10"
```

---

### Task 3: Implement CaseEngineHealthCheck

**Files:**
- Create: `src/main/java/io/casehub/flow/health/CaseEngineHealthCheck.java`

- [ ] **Step 1: Create the health check class**

Create `src/main/java/io/casehub/flow/health/CaseEngineHealthCheck.java`:

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
package io.casehub.flow.health;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@ApplicationScoped
@Readiness
public class CaseEngineHealthCheck implements HealthCheck {

    private final AtomicBoolean engineReady = new AtomicBoolean(false);

    void onStartup(@Observes StartupEvent event) {
        engineReady.set(true);
    }

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("Case engine")
                .status(engineReady.get())
                .build();
    }
}
```

- [ ] **Step 2: Run the unit tests — verify they pass**

Run:
```bash
./mvnw test -pl . -Dtest="CaseEngineHealthCheckTest" -q
```

Expected: 2 tests pass, 0 failures.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/casehub/flow/health/CaseEngineHealthCheck.java
git commit -m "feat: implement CaseEngineHealthCheck readiness probe

Refs #10"
```

---

### Task 4: Write integration tests for health endpoints

**Files:**
- Create: `src/test/java/io/casehub/flow/rest/HealthEndpointIT.java`

- [ ] **Step 1: Write the integration test class**

Create `src/test/java/io/casehub/flow/rest/HealthEndpointIT.java`:

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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthEndpointIT extends CaseHubIntegrationTestBase {

  @Test
  void livenessReturns200() {
    given()
        .when()
        .get("/q/health/live")
        .then()
        .statusCode(200)
        .body("status", is("UP"));
  }

  @Test
  void readinessReturns200WhenHealthy() {
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(200)
        .body("status", is("UP"));
  }

  @Test
  void readinessContainsCaseEngineCheck() {
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(200)
        .body("checks.name", hasItem("Case engine"))
        .body("checks.find { it.name == 'Case engine' }.status", is("UP"));
  }

  @Test
  void readinessContainsDatabaseCheck() {
    given()
        .when()
        .get("/q/health/ready")
        .then()
        .statusCode(200)
        .body("checks.find { it.name.contains('Reactive') }.status", is("UP"));
  }
}
```

The integration tests extend `CaseHubIntegrationTestBase` (which provides a real Testcontainers PostgreSQL DB). By the time the tests run, the Quarkus app has fully started, so `StartupEvent` has fired and the engine readiness flag is true.

- [ ] **Step 2: Run the integration tests**

Run:
```bash
./mvnw test -pl . -Dtest="HealthEndpointIT" -q
```

Expected: 4 tests pass, 0 failures.

- [ ] **Step 3: Run the full test suite to check for regressions**

Run:
```bash
./mvnw verify -pl . -q
```

Expected: all existing tests still pass alongside the new ones.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/io/casehub/flow/rest/HealthEndpointIT.java
git commit -m "test: add integration tests for health check endpoints

Refs #10"
```

---

### Task 5: Final verification and closing commit

- [ ] **Step 1: Verify health endpoints manually (smoke test)**

Start the app in dev mode:
```bash
./mvnw quarkus:dev
```

In another terminal:
```bash
curl -s http://localhost:8080/q/health/live | jq .
curl -s http://localhost:8080/q/health/ready | jq .
```

Expected liveness response:
```json
{
  "status": "UP",
  "checks": []
}
```

Expected readiness response (verify both checks present):
```json
{
  "status": "UP",
  "checks": [
    { "name": "Case engine", "status": "UP" },
    { "name": "Reactive PostgreSQL connection health check", "status": "UP" }
  ]
}
```

Stop dev mode after verification.

- [ ] **Step 2: Run full test suite one final time**

Run:
```bash
./mvnw verify -pl . -q
```

Expected: all tests pass.
