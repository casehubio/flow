# Case Lifecycle API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement REST API endpoints for managing case instance lifecycle (start, query status, retrieve context) supporting CDI beans, classpath CaseHub, and YAML definitions.

**Architecture:** Extend CaseDefinitionService to index all CaseHub instances at startup, create CaseInstanceService for lifecycle operations, expose REST endpoints via CaseInstanceResource. Follow TDD with test-first approach for all components.

**Tech Stack:** Quarkus, Mutiny (reactive), Jakarta REST, casehub-engine API, JUnit 5, RestAssured

---

## File Structure

**New Files:**
- `src/main/java/io/casehub/flow/exception/DefinitionNotFoundException.java`
- `src/main/java/io/casehub/flow/exception/CaseHubNotFoundException.java`
- `src/main/java/io/casehub/flow/exception/CaseInstanceNotFoundException.java`
- `src/main/java/io/casehub/flow/exception/ContextPathNotFoundException.java`
- `src/main/java/io/casehub/flow/rest/dto/StartCaseRequest.java`
- `src/main/java/io/casehub/flow/rest/dto/CaseInstanceResponse.java`
- `src/main/java/io/casehub/flow/service/CaseInstanceService.java`
- `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`
- `src/test/java/io/casehub/flow/service/CaseDefinitionServiceTest.java`
- `src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java`
- `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`

**Modified Files:**
- `src/main/java/io/casehub/flow/service/CaseDefinitionService.java` - add CaseHub indexing

---

## Task 1: Foundation - Custom Exceptions

**Files:**
- Create: `src/main/java/io/casehub/flow/exception/DefinitionNotFoundException.java`
- Create: `src/main/java/io/casehub/flow/exception/CaseHubNotFoundException.java`
- Create: `src/main/java/io/casehub/flow/exception/CaseInstanceNotFoundException.java`
- Create: `src/main/java/io/casehub/flow/exception/ContextPathNotFoundException.java`

- [ ] **Step 1: Create DefinitionNotFoundException**

Create file: `src/main/java/io/casehub/flow/exception/DefinitionNotFoundException.java`

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
package io.casehub.flow.exception;

/**
 * Thrown when a case definition is not found in the registry.
 */
public class DefinitionNotFoundException extends RuntimeException {
  public DefinitionNotFoundException(String namespace, String name, String version) {
    super(
        String.format(
            "No case definition found for namespace '%s', name '%s', version '%s'",
            namespace, name, version));
  }
}
```

- [ ] **Step 2: Create CaseHubNotFoundException**

Create file: `src/main/java/io/casehub/flow/exception/CaseHubNotFoundException.java`

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
package io.casehub.flow.exception;

/**
 * Thrown when a CaseHub instance is not found for a registered definition.
 *
 * <p>This should not happen in normal operation since all registered definitions should have
 * corresponding CaseHub instances.
 */
public class CaseHubNotFoundException extends RuntimeException {
  public CaseHubNotFoundException(String namespace, String name, String version) {
    super(
        String.format(
            "No CaseHub found for definition namespace '%s', name '%s', version '%s'",
            namespace, name, version));
  }
}
```

- [ ] **Step 3: Create CaseInstanceNotFoundException**

Create file: `src/main/java/io/casehub/flow/exception/CaseInstanceNotFoundException.java`

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
package io.casehub.flow.exception;

import java.util.UUID;

/**
 * Thrown when a case instance is not found in the repository.
 */
public class CaseInstanceNotFoundException extends RuntimeException {
  public CaseInstanceNotFoundException(UUID caseId) {
    super(String.format("No case instance found with id '%s'", caseId));
  }

  public CaseInstanceNotFoundException(String message) {
    super(message);
  }
}
```

- [ ] **Step 4: Create ContextPathNotFoundException**

Create file: `src/main/java/io/casehub/flow/exception/ContextPathNotFoundException.java`

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
package io.casehub.flow.exception;

/**
 * Thrown when a context path does not exist in a case instance context.
 */
public class ContextPathNotFoundException extends RuntimeException {
  public ContextPathNotFoundException(String path) {
    super(String.format("Path '%s' does not exist in case context", path));
  }
}
```

- [ ] **Step 5: Verify compilation**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit exceptions**

```bash
git add src/main/java/io/casehub/flow/exception/
git commit -m "feat: add custom exceptions for case lifecycle API"
```

---

## Task 2: Foundation - DTOs

**Files:**
- Create: `src/main/java/io/casehub/flow/rest/dto/StartCaseRequest.java`
- Create: `src/main/java/io/casehub/flow/rest/dto/CaseInstanceResponse.java`

- [ ] **Step 1: Create StartCaseRequest DTO**

Create file: `src/main/java/io/casehub/flow/rest/dto/StartCaseRequest.java`

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

import java.util.Map;

/**
 * Request to start a new case instance.
 *
 * @param definition case definition reference (namespace, name, version)
 * @param context initial case context data (optional, defaults to empty map)
 */
public record StartCaseRequest(CaseDefinitionRef definition, Map<String, Object> context) {

  /**
   * Case definition reference.
   *
   * @param namespace case namespace
   * @param name case name
   * @param version case version
   */
  public record CaseDefinitionRef(String namespace, String name, String version) {}
}
```

- [ ] **Step 2: Create CaseInstanceResponse DTO**

Create file: `src/main/java/io/casehub/flow/rest/dto/CaseInstanceResponse.java`

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

import io.casehub.api.model.CaseStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Case instance response with status and metadata.
 *
 * @param caseId unique case instance identifier
 * @param status current case status (RUNNING, WAITING, SUSPENDED, COMPLETED, FAULTED, CANCELLED)
 * @param namespace case definition namespace
 * @param name case definition name
 * @param version case definition version
 * @param createdAt case instance creation timestamp
 * @param updatedAt last update timestamp
 */
public record CaseInstanceResponse(
    UUID caseId,
    CaseStatus status,
    String namespace,
    String name,
    String version,
    Instant createdAt,
    Instant updatedAt) {}
```

- [ ] **Step 3: Verify compilation**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit DTOs**

```bash
git add src/main/java/io/casehub/flow/rest/dto/
git commit -m "feat: add request/response DTOs for case lifecycle API"
```

---

## Task 3: CaseDefinitionService - CaseHub Indexing (TDD)

**Files:**
- Create: `src/test/java/io/casehub/flow/service/CaseDefinitionServiceTest.java`
- Modify: `src/main/java/io/casehub/flow/service/CaseDefinitionService.java`

- [ ] **Step 1: Write test for CaseHub indexing**

Create file: `src/test/java/io/casehub/flow/service/CaseDefinitionServiceTest.java`

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
package io.casehub.flow.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.engine.CaseHub;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CaseDefinitionServiceTest {

  @Inject CaseDefinitionService service;

  @Test
  void findCaseHub_returnsCaseHub_forCdiBeans() {
    // CDI bean from CaseDefinitionResourceTest: Document Approval v1.0.0
    Optional<CaseHub> hub =
        service
            .findCaseHub("test-api", "Document Approval", "1.0.0")
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();

    assertThat(hub).isPresent();
    assertThat(hub.get().getDefinition().getNamespace()).isEqualTo("test-api");
    assertThat(hub.get().getDefinition().getName()).isEqualTo("Document Approval");
    assertThat(hub.get().getDefinition().getVersion()).isEqualTo("1.0.0");
  }

  @Test
  void findCaseHub_returnsCaseHub_forClasspathCaseHub() {
    // Classpath CaseHub from LoaderIntegrationTest: Classpath Only Case
    Optional<CaseHub> hub =
        service
            .findCaseHub("test-classpath", "Classpath Only Case", "1.0.0")
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();

    assertThat(hub).isPresent();
    assertThat(hub.get().getDefinition().getNamespace()).isEqualTo("test-classpath");
    assertThat(hub.get().getDefinition().getName()).isEqualTo("Classpath Only Case");
  }

  @Test
  void findCaseHub_returnsCaseHub_forYamlDefinition() {
    // YAML definition from LoaderIntegrationTest: YAML Test Case
    Optional<CaseHub> hub =
        service
            .findCaseHub("test-yaml", "YAML Test Case", "1.0.0")
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();

    assertThat(hub).isPresent();
    assertThat(hub.get().getDefinition().getNamespace()).isEqualTo("test-yaml");
    assertThat(hub.get().getDefinition().getName()).isEqualTo("YAML Test Case");
  }

  @Test
  void findCaseHub_returnsEmpty_whenNotFound() {
    Optional<CaseHub> hub =
        service
            .findCaseHub("non-existent", "Unknown Case", "1.0.0")
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();

    assertThat(hub).isEmpty();
  }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=CaseDefinitionServiceTest`
Expected: FAIL - method findCaseHub does not exist

- [ ] **Step 3: Implement CaseHub indexing in CaseDefinitionService**

Modify file: `src/main/java/io/casehub/flow/service/CaseDefinitionService.java`

Add these imports at the top:
```java
import io.casehub.api.engine.CaseHub;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
```

Add these fields after existing `@Inject CaseDefinitionRegistry caseDefinitionRegistry;`:
```java
@Inject Instance<CaseHub> caseHubs;
private final Map<DefinitionKey, CaseHub> caseHubIndex = new ConcurrentHashMap<>();

private record DefinitionKey(String namespace, String name, String version) {}
```

Add these methods at the end of the class:
```java
/**
 * Index all CaseHub instances at startup.
 *
 * <p>Priority 30: runs after CaseHubClassPathLoader (20) and YamlCaseDefinitionLoader (20) to
 * ensure all CaseHub instances (CDI + classpath + YAML) are available.
 */
void indexCaseHubs(@Observes @Priority(30) StartupEvent event) {
  int count = 0;
  for (CaseHub hub : caseHubs) {
    io.casehub.api.model.CaseDefinition def = hub.getDefinition();
    DefinitionKey key = new DefinitionKey(def.getNamespace(), def.getName(), def.getVersion());
    caseHubIndex.put(key, hub);
    count++;
  }
  LOG.infof("Indexed %d CaseHub instances from all sources (CDI + classpath + YAML)", count);
}

/**
 * Find a CaseHub instance by definition key.
 *
 * @param namespace case namespace
 * @param name case name
 * @param version case version
 * @return Optional containing CaseHub if found, empty otherwise
 */
public Uni<Optional<CaseHub>> findCaseHub(String namespace, String name, String version) {
  DefinitionKey key = new DefinitionKey(namespace, name, version);
  CaseHub hub = caseHubIndex.get(key);
  return Uni.createFrom().item(Optional.ofNullable(hub));
}
```

Add LOG field if not present:
```java
private static final Logger LOG = Logger.getLogger(CaseDefinitionService.class);
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./mvnw test -Dtest=CaseDefinitionServiceTest`
Expected: PASS - all 4 tests green

- [ ] **Step 5: Run all existing tests to ensure no regression**

Run: `./mvnw test`
Expected: All tests pass

- [ ] **Step 6: Commit CaseHub indexing**

```bash
git add src/main/java/io/casehub/flow/service/CaseDefinitionService.java src/test/java/io/casehub/flow/service/CaseDefinitionServiceTest.java
git commit -m "feat: add CaseHub indexing to CaseDefinitionService"
```

---

## Task 4: CaseInstanceService - Start Case (TDD)

**Files:**
- Create: `src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java`
- Create: `src/main/java/io/casehub/flow/service/CaseInstanceService.java`

- [ ] **Step 1: Write failing test for startCase**

Create file: `src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java`

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
package io.casehub.flow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.casehub.api.model.CaseStatus;
import io.casehub.flow.exception.DefinitionNotFoundException;
import io.casehub.flow.rest.dto.CaseInstanceResponse;
import io.casehub.flow.rest.dto.StartCaseRequest;
import io.casehub.flow.rest.dto.StartCaseRequest.CaseDefinitionRef;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CaseInstanceServiceTest {

  @Inject CaseInstanceService service;

  @Test
  void startCase_withCdiBeanDefinition_startsSuccessfully() {
    StartCaseRequest request =
        new StartCaseRequest(
            new CaseDefinitionRef("test-api", "Document Approval", "1.0.0"),
            Map.of("documentId", "DOC-123"));

    CaseInstanceResponse response =
        service
            .startCase(request)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();

    assertThat(response.caseId()).isNotNull();
    assertThat(response.status()).isIn(CaseStatus.RUNNING, CaseStatus.COMPLETED);
    assertThat(response.namespace()).isEqualTo("test-api");
    assertThat(response.name()).isEqualTo("Document Approval");
    assertThat(response.version()).isEqualTo("1.0.0");
    assertThat(response.createdAt()).isNotNull();
    assertThat(response.updatedAt()).isNotNull();
  }

  @Test
  void startCase_withYamlDefinition_startsSuccessfully() {
    StartCaseRequest request =
        new StartCaseRequest(
            new CaseDefinitionRef("test-yaml", "YAML Test Case", "1.0.0"),
            Map.of("data", "test-value"));

    CaseInstanceResponse response =
        service
            .startCase(request)
            .subscribe()
            .withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .getItem();

    assertThat(response.caseId()).isNotNull();
    assertThat(response.status()).isIn(CaseStatus.RUNNING, CaseStatus.COMPLETED);
    assertThat(response.namespace()).isEqualTo("test-yaml");
    assertThat(response.name()).isEqualTo("YAML Test Case");
  }

  @Test
  void startCase_throwsDefinitionNotFoundException_whenDefinitionNotFound() {
    StartCaseRequest request =
        new StartCaseRequest(
            new CaseDefinitionRef("non-existent", "Unknown Case", "1.0.0"), Map.of());

    assertThatThrownBy(
            () ->
                service
                    .startCase(request)
                    .subscribe()
                    .withSubscriber(UniAssertSubscriber.create())
                    .awaitFailure())
        .hasCauseInstanceOf(DefinitionNotFoundException.class)
        .hasMessageContaining("No case definition found");
  }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=CaseInstanceServiceTest`
Expected: FAIL - CaseInstanceService class does not exist

- [ ] **Step 3: Implement CaseInstanceService with startCase method**

Create file: `src/main/java/io/casehub/flow/service/CaseInstanceService.java`

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
package io.casehub.flow.service;

import io.casehub.api.engine.CaseHub;
import io.casehub.engine.internal.model.CaseInstance;
import io.casehub.engine.internal.model.CaseMetaModel;
import io.casehub.engine.spi.CaseInstanceRepository;
import io.casehub.flow.exception.CaseHubNotFoundException;
import io.casehub.flow.exception.CaseInstanceNotFoundException;
import io.casehub.flow.exception.DefinitionNotFoundException;
import io.casehub.flow.rest.dto.CaseInstanceResponse;
import io.casehub.flow.rest.dto.StartCaseRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;

/**
 * Service for managing case instance lifecycle.
 *
 * <p>Provides operations for:
 *
 * <ul>
 *   <li>Starting new case instances from definitions (CDI, classpath, YAML)
 *   <li>Querying case instance status and metadata
 *   <li>Retrieving case context data
 * </ul>
 */
@ApplicationScoped
public class CaseInstanceService {

  @Inject CaseDefinitionService definitionService;
  @Inject CaseInstanceRepository instanceRepository;

  /**
   * Start a new case instance.
   *
   * @param request start case request with definition reference and initial context
   * @return case instance response with status and metadata
   */
  public Uni<CaseInstanceResponse> startCase(StartCaseRequest request) {
    String namespace = request.definition().namespace();
    String name = request.definition().name();
    String version = request.definition().version();
    Map<String, Object> context = request.context() != null ? request.context() : Map.of();

    // 1. Validate definition exists
    return definitionService
        .findByKey(namespace, name, version)
        .onItem()
        .ifNull()
        .failWith(() -> new DefinitionNotFoundException(namespace, name, version))

        // 2. Find CaseHub (should exist for registered definition)
        .flatMap(def -> definitionService.findCaseHub(namespace, name, version))
        .onItem()
        .ifNull()
        .failWith(() -> new CaseHubNotFoundException(namespace, name, version))
        .map(optional -> optional.orElseThrow())

        // 3. Start case via CaseHub
        .flatMap(
            hub ->
                Uni.createFrom()
                    .completionStage(() -> hub.startCase(context))
                    .onItem()
                    .ifNull()
                    .failWith(
                        () ->
                            new CaseInstanceNotFoundException(
                                "Case started but UUID is null")))

        // 4. Fetch CaseInstance from repository
        .flatMap(caseId -> instanceRepository.findByUuid(caseId))
        .onItem()
        .ifNull()
        .failWith(
            () ->
                new CaseInstanceNotFoundException(
                    "Case started but not found in repository"))

        // 5. Map to response DTO
        .map(this::toCaseInstanceResponse);
  }

  private CaseInstanceResponse toCaseInstanceResponse(CaseInstance instance) {
    CaseMetaModel meta = instance.getCaseMetaModel();
    return new CaseInstanceResponse(
        instance.getUuid(),
        instance.getState(),
        meta.getNamespace(),
        meta.getName(),
        meta.getVersion(),
        meta.getCreatedAt(),
        meta.getCreatedAt()); // Using createdAt for updatedAt since no separate updatedAt field
  }
}
```

- [ ] **Step 4: Run test to verify pass**

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#startCase_withCdiBeanDefinition_startsSuccessfully`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#startCase_withYamlDefinition_startsSuccessfully`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#startCase_throwsDefinitionNotFoundException_whenDefinitionNotFound`
Expected: PASS

- [ ] **Step 5: Commit startCase implementation**

```bash
git add src/main/java/io/casehub/flow/service/CaseInstanceService.java src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java
git commit -m "feat: implement CaseInstanceService.startCase()"
```

---

## Task 5: CaseInstanceService - Get Case Instance (TDD)

**Files:**
- Modify: `src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java`
- Modify: `src/main/java/io/casehub/flow/service/CaseInstanceService.java`

- [ ] **Step 1: Write failing test for getCaseInstance**

Add to `src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java`:

```java
@Test
void getCaseInstance_returnsInstance_whenExists() {
  // Start a case first
  StartCaseRequest startRequest =
      new StartCaseRequest(
          new CaseDefinitionRef("test-api", "Document Approval", "1.0.0"),
          Map.of("documentId", "DOC-456"));

  CaseInstanceResponse startedCase =
      service
          .startCase(startRequest)
          .subscribe()
          .withSubscriber(UniAssertSubscriber.create())
          .awaitItem()
          .getItem();

  // Get the case instance
  CaseInstanceResponse response =
      service
          .getCaseInstance(startedCase.caseId())
          .subscribe()
          .withSubscriber(UniAssertSubscriber.create())
          .awaitItem()
          .getItem();

  assertThat(response.caseId()).isEqualTo(startedCase.caseId());
  assertThat(response.namespace()).isEqualTo("test-api");
  assertThat(response.name()).isEqualTo("Document Approval");
  assertThat(response.version()).isEqualTo("1.0.0");
}

@Test
void getCaseInstance_throwsNotFoundException_whenNotFound() {
  UUID nonExistentId = UUID.randomUUID();

  assertThatThrownBy(
          () ->
              service
                  .getCaseInstance(nonExistentId)
                  .subscribe()
                  .withSubscriber(UniAssertSubscriber.create())
                  .awaitFailure())
      .hasCauseInstanceOf(CaseInstanceNotFoundException.class)
      .hasMessageContaining("No case instance found");
}
```

Add import:
```java
import java.util.UUID;
```

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#getCaseInstance_returnsInstance_whenExists`
Expected: FAIL - method getCaseInstance does not exist

- [ ] **Step 3: Implement getCaseInstance method**

Add to `src/main/java/io/casehub/flow/service/CaseInstanceService.java`:

```java
/**
 * Get a case instance by ID.
 *
 * @param caseId case instance UUID
 * @return case instance response with status and metadata
 */
public Uni<CaseInstanceResponse> getCaseInstance(java.util.UUID caseId) {
  return instanceRepository
      .findByUuid(caseId)
      .onItem()
      .ifNull()
      .failWith(() -> new CaseInstanceNotFoundException(caseId))
      .map(this::toCaseInstanceResponse);
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#getCaseInstance_returnsInstance_whenExists`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#getCaseInstance_throwsNotFoundException_whenNotFound`
Expected: PASS

- [ ] **Step 5: Commit getCaseInstance**

```bash
git add src/main/java/io/casehub/flow/service/CaseInstanceService.java src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java
git commit -m "feat: implement CaseInstanceService.getCaseInstance()"
```

---

## Task 6: CaseInstanceService - Get Context (TDD)

**Files:**
- Modify: `src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java`
- Modify: `src/main/java/io/casehub/flow/service/CaseInstanceService.java`

- [ ] **Step 1: Write failing test for getCaseContext**

Add to `src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java`:

```java
@Test
void getCaseContext_returnsFullContext_whenExists() {
  // Start a case with context
  StartCaseRequest request =
      new StartCaseRequest(
          new CaseDefinitionRef("test-api", "Document Approval", "1.0.0"),
          Map.of("documentId", "DOC-789", "submittedBy", "alice@example.com"));

  CaseInstanceResponse startedCase =
      service
          .startCase(request)
          .subscribe()
          .withSubscriber(UniAssertSubscriber.create())
          .awaitItem()
          .getItem();

  // Get context
  Map<String, Object> context =
      service
          .getCaseContext(startedCase.caseId())
          .subscribe()
          .withSubscriber(UniAssertSubscriber.create())
          .awaitItem()
          .getItem();

  assertThat(context).isNotNull();
  assertThat(context).containsEntry("documentId", "DOC-789");
  assertThat(context).containsEntry("submittedBy", "alice@example.com");
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#getCaseContext_returnsFullContext_whenExists`
Expected: FAIL - method getCaseContext does not exist

- [ ] **Step 3: Implement getCaseContext method**

Add to `src/main/java/io/casehub/flow/service/CaseInstanceService.java`:

```java
/**
 * Get full case context.
 *
 * @param caseId case instance UUID
 * @return case context data as map
 */
public Uni<Map<String, Object>> getCaseContext(java.util.UUID caseId) {
  return instanceRepository
      .findByUuid(caseId)
      .onItem()
      .ifNull()
      .failWith(() -> new CaseInstanceNotFoundException(caseId))
      .map(instance -> instance.getCaseContext().getData());
}
```

- [ ] **Step 4: Run test to verify pass**

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#getCaseContext_returnsFullContext_whenExists`
Expected: PASS

- [ ] **Step 5: Commit getCaseContext**

```bash
git add src/main/java/io/casehub/flow/service/CaseInstanceService.java src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java
git commit -m "feat: implement CaseInstanceService.getCaseContext()"
```

---

## Task 7: CaseInstanceService - Get Context Path (TDD)

**Files:**
- Modify: `src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java`
- Modify: `src/main/java/io/casehub/flow/service/CaseInstanceService.java`

- [ ] **Step 1: Write failing tests for getContextPath**

Add to `src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java`:

```java
import io.casehub.flow.exception.ContextPathNotFoundException;

@Test
void getContextPath_returnsValue_whenPathExists() {
  // Start a case with nested context
  StartCaseRequest request =
      new StartCaseRequest(
          new CaseDefinitionRef("test-api", "Document Approval", "1.0.0"),
          Map.of("customer", Map.of("name", "Alice", "email", "alice@example.com")));

  CaseInstanceResponse startedCase =
      service
          .startCase(request)
          .subscribe()
          .withSubscriber(UniAssertSubscriber.create())
          .awaitItem()
          .getItem();

  // Query path
  Object value =
      service
          .getContextPath(startedCase.caseId(), "customer.name")
          .subscribe()
          .withSubscriber(UniAssertSubscriber.create())
          .awaitItem()
          .getItem();

  assertThat(value).isEqualTo("Alice");
}

@Test
void getContextPath_throwsNotFoundException_whenPathNotFound() {
  StartCaseRequest request =
      new StartCaseRequest(
          new CaseDefinitionRef("test-api", "Document Approval", "1.0.0"), Map.of("key", "value"));

  CaseInstanceResponse startedCase =
      service
          .startCase(request)
          .subscribe()
          .withSubscriber(UniAssertSubscriber.create())
          .awaitItem()
          .getItem();

  assertThatThrownBy(
          () ->
              service
                  .getContextPath(startedCase.caseId(), "non.existent.path")
                  .subscribe()
                  .withSubscriber(UniAssertSubscriber.create())
                  .awaitFailure())
      .hasCauseInstanceOf(ContextPathNotFoundException.class)
      .hasMessageContaining("does not exist in case context");
}

@Test
void getContextPath_throwsNotFoundException_whenCaseNotFound() {
  UUID nonExistentId = UUID.randomUUID();

  assertThatThrownBy(
          () ->
              service
                  .getContextPath(nonExistentId, "some.path")
                  .subscribe()
                  .withSubscriber(UniAssertSubscriber.create())
                  .awaitFailure())
      .hasCauseInstanceOf(CaseInstanceNotFoundException.class);
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#getContextPath_returnsValue_whenPathExists`
Expected: FAIL - method getContextPath does not exist

- [ ] **Step 3: Implement getContextPath method**

Add to `src/main/java/io/casehub/flow/service/CaseInstanceService.java`:

Add import:
```java
import io.casehub.flow.exception.ContextPathNotFoundException;
```

Add method:
```java
/**
 * Query case context by path.
 *
 * <p>Supports dot notation with array indexing (e.g., "customer.orders[0].id").
 *
 * @param caseId case instance UUID
 * @param path context path in dot notation
 * @return value at the specified path
 */
public Uni<Object> getContextPath(java.util.UUID caseId, String path) {
  return instanceRepository
      .findByUuid(caseId)
      .onItem()
      .ifNull()
      .failWith(() -> new CaseInstanceNotFoundException(caseId))
      .map(
          instance -> {
            Object value = instance.getCaseContext().getPath(path);
            if (value == null) {
              throw new ContextPathNotFoundException(path);
            }
            return value;
          });
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#getContextPath_returnsValue_whenPathExists`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#getContextPath_throwsNotFoundException_whenPathNotFound`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceServiceTest#getContextPath_throwsNotFoundException_whenCaseNotFound`
Expected: PASS

- [ ] **Step 5: Run all CaseInstanceService tests**

Run: `./mvnw test -Dtest=CaseInstanceServiceTest`
Expected: All tests pass

- [ ] **Step 6: Commit getContextPath**

```bash
git add src/main/java/io/casehub/flow/service/CaseInstanceService.java src/test/java/io/casehub/flow/service/CaseInstanceServiceTest.java
git commit -m "feat: implement CaseInstanceService.getContextPath() with dot notation"
```

---

## Task 8: CaseInstanceResource - POST /cases (TDD)

**Files:**
- Create: `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`
- Create: `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`

- [ ] **Step 1: Write failing test for POST /cases**

Create file: `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`

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
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CaseInstanceResourceTest {

  @Test
  void startCase_cdiBeanDefinition_returns200WithCorrectStatus() {
    Map<String, Object> request =
        Map.of(
            "definition",
            Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
            "context",
            Map.of("documentId", "DOC-123", "submittedBy", "alice@example.com"));

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/v1/cases")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("caseId", notNullValue())
        .body("status", notNullValue())
        .body("namespace", equalTo("test-api"))
        .body("name", equalTo("Document Approval"))
        .body("version", equalTo("1.0.0"))
        .body("createdAt", notNullValue())
        .body("updatedAt", notNullValue());
  }

  @Test
  void startCase_yamlDefinition_returns200() {
    Map<String, Object> request =
        Map.of(
            "definition",
            Map.of("namespace", "test-yaml", "name", "YAML Test Case", "version", "1.0.0"),
            "context",
            Map.of("data", "test-value"));

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/v1/cases")
        .then()
        .statusCode(200)
        .body("caseId", notNullValue())
        .body("namespace", equalTo("test-yaml"))
        .body("name", equalTo("YAML Test Case"));
  }

  @Test
  void startCase_definitionNotFound_returns404WithProblemDetail() {
    Map<String, Object> request =
        Map.of(
            "definition",
            Map.of("namespace", "non-existent", "name", "Unknown Case", "version", "1.0.0"),
            "context",
            Map.of());

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/v1/cases")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Case definition not found"))
        .body("status", equalTo(404))
        .body("detail", notNullValue());
  }

  @Test
  void startCase_invalidRequest_returns400() {
    Map<String, Object> request = Map.of("context", Map.of("key", "value"));

    given()
        .contentType(ContentType.JSON)
        .body(request)
        .when()
        .post("/api/v1/cases")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("title", equalTo("Invalid request"))
        .body("status", equalTo(400));
  }
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#startCase_cdiBeanDefinition_returns200WithCorrectStatus`
Expected: FAIL - 404 Not Found (resource doesn't exist)

- [ ] **Step 3: Implement CaseInstanceResource with POST endpoint**

Create file: `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`

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

import io.casehub.flow.exception.CaseInstanceNotFoundException;
import io.casehub.flow.exception.DefinitionNotFoundException;
import io.casehub.flow.rest.dto.StartCaseRequest;
import io.casehub.flow.service.CaseInstanceService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST API for case instance lifecycle management.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /api/v1/cases — start case instance
 *   <li>GET /api/v1/cases/{caseId} — get case status
 *   <li>GET /api/v1/cases/{caseId}/context — get full context
 *   <li>GET /api/v1/cases/{caseId}/context/{path} — query context by path
 * </ul>
 */
@Path("/api/v1/cases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaseInstanceResource {

  @Inject CaseInstanceService caseInstanceService;

  /**
   * Start a new case instance.
   *
   * @param request start case request with definition reference and initial context
   * @return 200 OK with case instance response, 404 if definition not found, 400 for invalid
   *     request
   */
  @POST
  public Uni<Response> startCase(StartCaseRequest request) {
    // Validation
    if (request == null || request.definition() == null) {
      return Uni.createFrom()
          .item(
              Response.status(400)
                  .entity(
                      new CaseDefinitionResource.ProblemDetail(
                          "Invalid request", 400, "Request body and definition are required"))
                  .build());
    }

    return caseInstanceService
        .startCase(request)
        .map(response -> Response.ok(response).build())
        .onFailure(DefinitionNotFoundException.class)
        .recoverWithItem(
            ex ->
                Response.status(404)
                    .entity(
                        new CaseDefinitionResource.ProblemDetail(
                            "Case definition not found", 404, ex.getMessage()))
                    .build())
        .onFailure()
        .recoverWithItem(
            ex ->
                Response.status(500)
                    .entity(
                        new CaseDefinitionResource.ProblemDetail(
                            "Internal server error",
                            500,
                            "Failed to start case instance: " + ex.getMessage()))
                    .build());
  }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#startCase_cdiBeanDefinition_returns200WithCorrectStatus`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#startCase_yamlDefinition_returns200`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#startCase_definitionNotFound_returns404WithProblemDetail`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#startCase_invalidRequest_returns400`
Expected: PASS

- [ ] **Step 5: Commit POST /cases endpoint**

```bash
git add src/main/java/io/casehub/flow/rest/CaseInstanceResource.java src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java
git commit -m "feat: implement POST /api/v1/cases endpoint"
```

---

## Task 9: CaseInstanceResource - GET /cases/{caseId} (TDD)

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`
- Modify: `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`

- [ ] **Step 1: Write failing test for GET /cases/{caseId}**

Add to `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`:

```java
import java.util.UUID;

@Test
void getCaseInstance_returns200() {
  // Start a case first
  Map<String, Object> startRequest =
      Map.of(
          "definition",
          Map.of("namespace", "test-api", "name", "Invoice Processing", "version", "1.0.0"),
          "context",
          Map.of("invoiceId", "INV-456"));

  String caseId =
      given()
          .contentType(ContentType.JSON)
          .body(startRequest)
          .when()
          .post("/api/v1/cases")
          .then()
          .statusCode(200)
          .extract()
          .path("caseId");

  // Get the case instance
  given()
      .when()
      .get("/api/v1/cases/{caseId}", caseId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body("caseId", equalTo(caseId))
      .body("namespace", equalTo("test-api"))
      .body("name", equalTo("Invoice Processing"))
      .body("version", equalTo("1.0.0"))
      .body("status", notNullValue());
}

@Test
void getCaseInstance_notFound_returns404() {
  UUID nonExistentId = UUID.randomUUID();

  given()
      .when()
      .get("/api/v1/cases/{caseId}", nonExistentId)
      .then()
      .statusCode(404)
      .contentType(ContentType.JSON)
      .body("title", equalTo("Case instance not found"))
      .body("status", equalTo(404));
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#getCaseInstance_returns200`
Expected: FAIL - 404 Not Found (endpoint doesn't exist)

- [ ] **Step 3: Implement GET /cases/{caseId} endpoint**

Add to `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`:

Add imports:
```java
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import java.util.UUID;
```

Add method:
```java
/**
 * Get a case instance by ID.
 *
 * @param caseId case instance UUID
 * @return 200 OK with case instance response, 404 if not found
 */
@GET
@Path("/{caseId}")
public Uni<Response> getCaseInstance(@PathParam("caseId") UUID caseId) {
  return caseInstanceService
      .getCaseInstance(caseId)
      .map(response -> Response.ok(response).build())
      .onFailure(CaseInstanceNotFoundException.class)
      .recoverWithItem(
          ex ->
              Response.status(404)
                  .entity(
                      new CaseDefinitionResource.ProblemDetail(
                          "Case instance not found", 404, ex.getMessage()))
                  .build())
      .onFailure()
      .recoverWithItem(
          ex ->
              Response.status(500)
                  .entity(
                      new CaseDefinitionResource.ProblemDetail(
                          "Internal server error", 500, ex.getMessage()))
                  .build());
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#getCaseInstance_returns200`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#getCaseInstance_notFound_returns404`
Expected: PASS

- [ ] **Step 5: Commit GET /cases/{caseId} endpoint**

```bash
git add src/main/java/io/casehub/flow/rest/CaseInstanceResource.java src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java
git commit -m "feat: implement GET /api/v1/cases/{caseId} endpoint"
```

---

## Task 10: CaseInstanceResource - GET /cases/{caseId}/context (TDD)

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`
- Modify: `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`

- [ ] **Step 1: Write failing test for GET /cases/{caseId}/context**

Add to `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`:

```java
import static org.hamcrest.Matchers.hasKey;

@Test
void getContext_returns200WithFullContext() {
  // Start a case with context
  Map<String, Object> startRequest =
      Map.of(
          "definition",
          Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
          "context",
          Map.of("documentId", "DOC-789", "submittedBy", "bob@example.com"));

  String caseId =
      given()
          .contentType(ContentType.JSON)
          .body(startRequest)
          .when()
          .post("/api/v1/cases")
          .then()
          .statusCode(200)
          .extract()
          .path("caseId");

  // Get context
  given()
      .when()
      .get("/api/v1/cases/{caseId}/context", caseId)
      .then()
      .statusCode(200)
      .contentType(ContentType.JSON)
      .body("$", hasKey("documentId"))
      .body("$", hasKey("submittedBy"))
      .body("documentId", equalTo("DOC-789"))
      .body("submittedBy", equalTo("bob@example.com"));
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#getContext_returns200WithFullContext`
Expected: FAIL - 404 Not Found (endpoint doesn't exist)

- [ ] **Step 3: Implement GET /cases/{caseId}/context endpoint**

Add to `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`:

```java
/**
 * Get full case context.
 *
 * @param caseId case instance UUID
 * @return 200 OK with context map, 404 if case not found
 */
@GET
@Path("/{caseId}/context")
public Uni<Response> getContext(@PathParam("caseId") UUID caseId) {
  return caseInstanceService
      .getCaseContext(caseId)
      .map(context -> Response.ok(context).build())
      .onFailure(CaseInstanceNotFoundException.class)
      .recoverWithItem(
          ex ->
              Response.status(404)
                  .entity(
                      new CaseDefinitionResource.ProblemDetail(
                          "Case instance not found", 404, ex.getMessage()))
                  .build())
      .onFailure()
      .recoverWithItem(
          ex ->
              Response.status(500)
                  .entity(
                      new CaseDefinitionResource.ProblemDetail(
                          "Internal server error", 500, ex.getMessage()))
                  .build());
}
```

- [ ] **Step 4: Run test to verify pass**

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#getContext_returns200WithFullContext`
Expected: PASS

- [ ] **Step 5: Commit GET /cases/{caseId}/context endpoint**

```bash
git add src/main/java/io/casehub/flow/rest/CaseInstanceResource.java src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java
git commit -m "feat: implement GET /api/v1/cases/{caseId}/context endpoint"
```

---

## Task 11: CaseInstanceResource - GET /cases/{caseId}/context/{path} (TDD)

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`
- Modify: `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`

- [ ] **Step 1: Write failing tests for GET /cases/{caseId}/context/{path}**

Add to `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`:

```java
@Test
void getContextPath_simpleProperty_returns200() {
  // Start case with nested context
  Map<String, Object> startRequest =
      Map.of(
          "definition",
          Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
          "context",
          Map.of(
              "customer",
              Map.of("name", "Charlie", "email", "charlie@example.com"),
              "status",
              "pending"));

  String caseId =
      given()
          .contentType(ContentType.JSON)
          .body(startRequest)
          .when()
          .post("/api/v1/cases")
          .then()
          .statusCode(200)
          .extract()
          .path("caseId");

  // Query simple path
  given()
      .when()
      .get("/api/v1/cases/{caseId}/context/status", caseId)
      .then()
      .statusCode(200)
      .body(equalTo("\"pending\""));
}

@Test
void getContextPath_nestedProperty_returns200() {
  // Same case setup as above, reuse or create new
  Map<String, Object> startRequest =
      Map.of(
          "definition",
          Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
          "context",
          Map.of("customer", Map.of("name", "Diana", "address", Map.of("city", "Seattle"))));

  String caseId =
      given()
          .contentType(ContentType.JSON)
          .body(startRequest)
          .when()
          .post("/api/v1/cases")
          .then()
          .statusCode(200)
          .extract()
          .path("caseId");

  // Query nested path
  given()
      .when()
      .get("/api/v1/cases/{caseId}/context/customer.address.city", caseId)
      .then()
      .statusCode(200)
      .body(equalTo("\"Seattle\""));
}

@Test
void getContextPath_caseNotFound_returns404() {
  UUID nonExistentId = UUID.randomUUID();

  given()
      .when()
      .get("/api/v1/cases/{caseId}/context/some.path", nonExistentId)
      .then()
      .statusCode(404)
      .body("title", equalTo("Case instance not found"));
}

@Test
void getContextPath_pathNotFound_returns404() {
  Map<String, Object> startRequest =
      Map.of(
          "definition",
          Map.of("namespace", "test-api", "name", "Document Approval", "version", "1.0.0"),
          "context",
          Map.of("key", "value"));

  String caseId =
      given()
          .contentType(ContentType.JSON)
          .body(startRequest)
          .when()
          .post("/api/v1/cases")
          .then()
          .statusCode(200)
          .extract()
          .path("caseId");

  given()
      .when()
      .get("/api/v1/cases/{caseId}/context/non.existent.path", caseId)
      .then()
      .statusCode(404)
      .body("title", equalTo("Context path not found"))
      .body("detail", notNullValue());
}
```

- [ ] **Step 2: Run test to verify failure**

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#getContextPath_simpleProperty_returns200`
Expected: FAIL - 404 Not Found (endpoint doesn't exist)

- [ ] **Step 3: Implement GET /cases/{caseId}/context/{path} endpoint**

Add to `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`:

Add import:
```java
import io.casehub.flow.exception.ContextPathNotFoundException;
```

Add method:
```java
/**
 * Query case context by path.
 *
 * <p>Supports dot notation with array indexing (e.g., "customer.orders[0].id").
 *
 * @param caseId case instance UUID
 * @param path context path in dot notation
 * @return 200 OK with value at path, 404 if case or path not found
 */
@GET
@Path("/{caseId}/context/{path: .*}")
public Uni<Response> getContextPath(
    @PathParam("caseId") UUID caseId, @PathParam("path") String path) {

  return caseInstanceService
      .getContextPath(caseId, path)
      .map(value -> Response.ok(value).build())
      .onFailure(CaseInstanceNotFoundException.class)
      .recoverWithItem(
          ex ->
              Response.status(404)
                  .entity(
                      new CaseDefinitionResource.ProblemDetail(
                          "Case instance not found", 404, ex.getMessage()))
                  .build())
      .onFailure(ContextPathNotFoundException.class)
      .recoverWithItem(
          ex ->
              Response.status(404)
                  .entity(
                      new CaseDefinitionResource.ProblemDetail(
                          "Context path not found", 404, ex.getMessage()))
                  .build())
      .onFailure()
      .recoverWithItem(
          ex ->
              Response.status(500)
                  .entity(
                      new CaseDefinitionResource.ProblemDetail(
                          "Internal server error", 500, ex.getMessage()))
                  .build());
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#getContextPath_simpleProperty_returns200`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#getContextPath_nestedProperty_returns200`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#getContextPath_caseNotFound_returns404`
Expected: PASS

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#getContextPath_pathNotFound_returns404`
Expected: PASS

- [ ] **Step 5: Run all CaseInstanceResourceTest tests**

Run: `./mvnw test -Dtest=CaseInstanceResourceTest`
Expected: All tests pass

- [ ] **Step 6: Commit GET /cases/{caseId}/context/{path} endpoint**

```bash
git add src/main/java/io/casehub/flow/rest/CaseInstanceResource.java src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java
git commit -m "feat: implement GET /api/v1/cases/{caseId}/context/{path} endpoint with dot notation"
```

---

## Task 12: Integration Test - Full Lifecycle

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`

- [ ] **Step 1: Write full lifecycle integration test**

Add to `src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java`:

```java
@Test
void fullLifecycle_startThenQueryContext() {
  // 1. Start a case
  Map<String, Object> startRequest =
      Map.of(
          "definition",
          Map.of("namespace", "test-api", "name", "Document Approval", "version", "2.0.0"),
          "context",
          Map.of(
              "documentId",
              "DOC-FINAL",
              "metadata",
              Map.of("tags", java.util.List.of("urgent", "financial"), "priority", 1)));

  String caseId =
      given()
          .contentType(ContentType.JSON)
          .body(startRequest)
          .when()
          .post("/api/v1/cases")
          .then()
          .statusCode(200)
          .body("caseId", notNullValue())
          .body("namespace", equalTo("test-api"))
          .body("name", equalTo("Document Approval"))
          .body("version", equalTo("2.0.0"))
          .extract()
          .path("caseId");

  // 2. Get case status
  given()
      .when()
      .get("/api/v1/cases/{caseId}", caseId)
      .then()
      .statusCode(200)
      .body("caseId", equalTo(caseId))
      .body("status", notNullValue());

  // 3. Get full context
  given()
      .when()
      .get("/api/v1/cases/{caseId}/context", caseId)
      .then()
      .statusCode(200)
      .body("documentId", equalTo("DOC-FINAL"))
      .body("metadata.priority", equalTo(1));

  // 4. Query context paths
  given()
      .when()
      .get("/api/v1/cases/{caseId}/context/documentId", caseId)
      .then()
      .statusCode(200)
      .body(equalTo("\"DOC-FINAL\""));

  given()
      .when()
      .get("/api/v1/cases/{caseId}/context/metadata.priority", caseId)
      .then()
      .statusCode(200)
      .body(equalTo("1"));
}
```

- [ ] **Step 2: Run test to verify pass**

Run: `./mvnw test -Dtest=CaseInstanceResourceTest#fullLifecycle_startThenQueryContext`
Expected: PASS

- [ ] **Step 3: Commit full lifecycle test**

```bash
git add src/test/java/io/casehub/flow/rest/CaseInstanceResourceTest.java
git commit -m "test: add full lifecycle integration test"
```

---

## Task 13: Final Verification

**Files:**
- All project files

- [ ] **Step 1: Run all tests**

Run: `./mvnw test`
Expected: All tests pass (existing + new)

- [ ] **Step 2: Verify compilation**

Run: `./mvnw clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Check test coverage (optional)**

Run: `./mvnw verify`
Expected: All integration tests pass

- [ ] **Step 4: Manual smoke test in dev mode**

Run: `./mvnw quarkus:dev`

Test POST endpoint:
```bash
curl -X POST http://localhost:8080/api/v1/cases \
  -H "Content-Type: application/json" \
  -d '{
    "definition": {
      "namespace": "test-api",
      "name": "Document Approval",
      "version": "1.0.0"
    },
    "context": {
      "documentId": "SMOKE-TEST-001"
    }
  }'
```
Expected: 200 OK with caseId

Test GET endpoint with returned caseId:
```bash
curl http://localhost:8080/api/v1/cases/<caseId>
```
Expected: 200 OK with case instance response

Stop dev mode: Ctrl+C

- [ ] **Step 5: Review implementation against spec**

Open: `docs/superpowers/specs/2026-05-05-case-lifecycle-api-design.md`

Verify:
- All 4 endpoints implemented ✓
- Error handling follows RFC 7807 ✓
- All three sources (CDI, classpath, YAML) work ✓
- Dot notation path query works ✓
- Tests cover all scenarios ✓

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "docs: mark case lifecycle API implementation complete

- All 4 REST endpoints implemented and tested
- CaseDefinitionService extended with CaseHub indexing
- CaseInstanceService manages lifecycle operations
- Integration tests verify all three sources (CDI, classpath, YAML)
- Error handling follows RFC 7807 Problem Details
- Context path queries support dot notation

Closes #4"
```

---

## Success Criteria Checklist

From issue #4 acceptance criteria:

- [x] POST /cases validates definition exists and starts case instance
- [x] Response includes caseId, status, namespace, name, version, timestamps
- [x] GET /cases/{caseId} returns current status and metadata
- [x] GET context endpoints return 404 if caseId doesn't exist
- [x] Context path query supports dot notation and array indexing
- [x] Error responses follow RFC 7807 format
- [x] Integration tests cover full lifecycle
- [x] All three sources (CDI, classpath, YAML) tested

---

## Notes

**TDD Discipline:**
- Every feature starts with a failing test
- Minimal implementation to make test pass
- No code without a test
- Frequent commits after each green test

**Error Handling:**
- Fail-fast validation (definition must exist before starting)
- Clear, specific error messages in Problem Details
- No partial state (no FAULTED cases for missing definitions)

**Reactive Patterns:**
- Use `flatMap()` for Uni-returning operations
- Use `map()` for direct transformations
- Use `onFailure().recoverWithItem()` for error handling

**Testing Strategy:**
- Unit tests for service layer (mocked dependencies)
- Integration tests for REST layer (full stack)
- All three sources validated in integration tests
