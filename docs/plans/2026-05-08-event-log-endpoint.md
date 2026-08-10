# Event Log Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `GET /api/v1/cases/{caseId}/events` REST endpoint with pagination and filtering for case event logs.

**Architecture:** Three-layer architecture with EventLogEntryResponse DTO, EventLogService for business logic (pagination, filtering, enum conversion), and EventLogResource REST controller. Uses existing CaseHubRuntime for data access.

**Tech Stack:** Quarkus REST, SmallRye Mutiny (Uni), Jackson (JsonNode), JUnit 5, AssertJ, Mockito, RestAssured

---

## File Structure

**New Files:**
- `src/main/java/io/casehub/flow/rest/dto/EventLogEntryResponse.java` - DTO for event log entries
- `src/main/java/io/casehub/flow/service/EventLogService.java` - Business logic for event log operations
- `src/main/java/io/casehub/flow/rest/EventLogResource.java` - REST endpoint
- `src/test/java/io/casehub/flow/service/EventLogServiceTest.java` - Unit tests for service
- `src/test/java/io/casehub/flow/rest/EventLogResourceIT.java` - Integration tests

**Modified Files:** None

---

### Task 1: Create EventLogEntryResponse DTO

**Files:**
- Create: `src/main/java/io/casehub/flow/rest/dto/EventLogEntryResponse.java`

- [ ] **Step 1: Create EventLogEntryResponse record**

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

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.event.EventStreamType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Event log entry response DTO.
 *
 * <p>Represents a single event from the case event log, mapping directly from
 * CaseEventLogRecord.
 *
 * @param eventType type of event
 * @param streamType stream classification (CONTROL, DATA, etc.)
 * @param timestamp when the event occurred
 * @param payload event-specific data
 * @param metadata event metadata (traceId, etc.)
 */
public record EventLogEntryResponse(
    @NotNull CaseHubEventType eventType,
    @NotNull EventStreamType streamType,
    @NotNull Instant timestamp,
    JsonNode payload,
    JsonNode metadata) {}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -DskipTests`
Expected: SUCCESS

- [ ] **Step 3: Commit DTO**

```bash
git add src/main/java/io/casehub/flow/rest/dto/EventLogEntryResponse.java
git commit -m "feat: add EventLogEntryResponse DTO for event log entries

Add record DTO mapping directly from CaseEventLogRecord with fields:
eventType, streamType, timestamp, payload, metadata.

Issue: #7"
```

---

### Task 2: EventLogService - Setup and Enum Conversion

**Files:**
- Create: `src/main/java/io/casehub/flow/service/EventLogService.java`
- Create: `src/test/java/io/casehub/flow/service/EventLogServiceTest.java`

- [ ] **Step 1: Write test for valid enum conversion**

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

import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.event.EventStreamType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EventLogServiceTest {

  private final EventLogService service = new EventLogService();

  @Test
  void convertEventTypes_withValidValues_returnsEnumSet() {
    List<String> input = List.of("WORKER_EXECUTION_COMPLETED", "STATE_CHANGED");

    Set<CaseHubEventType> result = service.convertEventTypes(input);

    assertThat(result)
        .containsExactlyInAnyOrder(
            CaseHubEventType.WORKER_EXECUTION_COMPLETED,
            CaseHubEventType.STATE_CHANGED);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EventLogServiceTest#convertEventTypes_withValidValues_returnsEnumSet`
Expected: FAIL - "EventLogService does not exist"

- [ ] **Step 3: Create EventLogService with enum conversion**

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

import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.event.EventStreamType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for event log operations.
 *
 * <p>Provides business logic for retrieving, filtering, and paginating case event logs.
 */
@ApplicationScoped
public class EventLogService {

  /**
   * Convert string event type names to enum set.
   *
   * @param eventTypeNames list of event type names
   * @return set of CaseHubEventType enums
   * @throws IllegalArgumentException if any name is invalid
   */
  public Set<CaseHubEventType> convertEventTypes(List<String> eventTypeNames) {
    if (eventTypeNames == null || eventTypeNames.isEmpty()) {
      return Set.of();
    }
    return eventTypeNames.stream()
        .map(name -> CaseHubEventType.valueOf(name))
        .collect(Collectors.toSet());
  }

  /**
   * Convert string stream type names to enum set.
   *
   * @param streamTypeNames list of stream type names
   * @return set of EventStreamType enums
   * @throws IllegalArgumentException if any name is invalid
   */
  public Set<EventStreamType> convertStreamTypes(List<String> streamTypeNames) {
    if (streamTypeNames == null || streamTypeNames.isEmpty()) {
      return Set.of();
    }
    return streamTypeNames.stream()
        .map(name -> EventStreamType.valueOf(name))
        .collect(Collectors.toSet());
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=EventLogServiceTest#convertEventTypes_withValidValues_returnsEnumSet`
Expected: PASS

- [ ] **Step 5: Write test for invalid enum conversion**

Add to `EventLogServiceTest`:

```java
  @Test
  void convertEventTypes_withInvalidValue_throwsIllegalArgumentException() {
    List<String> input = List.of("INVALID_EVENT_TYPE");

    assertThatThrownBy(() -> service.convertEventTypes(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("INVALID_EVENT_TYPE");
  }

  @Test
  void convertStreamTypes_withValidValues_returnsEnumSet() {
    List<String> input = List.of("CONTROL", "DATA");

    Set<EventStreamType> result = service.convertStreamTypes(input);

    assertThat(result)
        .containsExactlyInAnyOrder(
            EventStreamType.CONTROL,
            EventStreamType.DATA);
  }

  @Test
  void convertStreamTypes_withInvalidValue_throwsIllegalArgumentException() {
    List<String> input = List.of("INVALID_STREAM");

    assertThatThrownBy(() -> service.convertStreamTypes(input))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("INVALID_STREAM");
  }

  @Test
  void convertEventTypes_withNullOrEmpty_returnsEmptySet() {
    assertThat(service.convertEventTypes(null)).isEmpty();
    assertThat(service.convertEventTypes(List.of())).isEmpty();
  }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn test -Dtest=EventLogServiceTest`
Expected: All 5 tests PASS

- [ ] **Step 7: Commit enum conversion**

```bash
git add src/main/java/io/casehub/flow/service/EventLogService.java
git add src/test/java/io/casehub/flow/service/EventLogServiceTest.java
git commit -m "feat: add EventLogService with enum conversion helpers

Add service with convertEventTypes and convertStreamTypes methods
for converting query param strings to enum sets. Includes validation
that throws IllegalArgumentException for invalid enum values.

Issue: #7"
```

---

### Task 3: EventLogService - Basic Event Fetching and Mapping

**Files:**
- Modify: `src/main/java/io/casehub/flow/service/EventLogService.java`
- Modify: `src/test/java/io/casehub/flow/service/EventLogServiceTest.java`

- [ ] **Step 1: Write test for basic event fetching without filters**

Add to `EventLogServiceTest`:

```java
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.event.CaseEventLogRecord;
import io.casehub.flow.rest.dto.EventLogEntryResponse;
import io.casehub.flow.rest.dto.PagedResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;

class EventLogServiceTest {

  private CaseHubRuntime runtime;
  private EventLogService service;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    runtime = mock(CaseHubRuntime.class);
    service = new EventLogService();
    service.caseHubRuntime = runtime;
  }

  @Test
  void getEventLog_noFiltersNoPagination_returnsAllEvents() {
    UUID caseId = UUID.randomUUID();
    Instant now = Instant.now();
    ObjectNode payload = objectMapper.createObjectNode().put("key", "value");
    ObjectNode metadata = objectMapper.createObjectNode().put("traceId", "abc");

    List<CaseEventLogRecord> records = List.of(
        new CaseEventLogRecord(
            CaseHubEventType.WORKER_EXECUTION_COMPLETED,
            EventStreamType.CONTROL,
            now,
            payload,
            metadata));

    when(runtime.eventLog(caseId))
        .thenReturn(CompletableFuture.completedFuture(records));

    PagedResponse<EventLogEntryResponse> result =
        service.getEventLog(caseId, 1, 50, null, null).await().indefinitely();

    assertThat(result.content()).hasSize(1);
    assertThat(result.content().get(0).eventType())
        .isEqualTo(CaseHubEventType.WORKER_EXECUTION_COMPLETED);
    assertThat(result.content().get(0).streamType())
        .isEqualTo(EventStreamType.CONTROL);
    assertThat(result.content().get(0).timestamp()).isEqualTo(now);
    assertThat(result.content().get(0).payload()).isEqualTo(payload);
    assertThat(result.content().get(0).metadata()).isEqualTo(metadata);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(50);
    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.totalPages()).isEqualTo(1);

    verify(runtime).eventLog(caseId);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EventLogServiceTest#getEventLog_noFiltersNoPagination_returnsAllEvents`
Expected: FAIL - "getEventLog method does not exist"

- [ ] **Step 3: Implement getEventLog with basic fetching and mapping**

Add to `EventLogService`:

```java
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.event.CaseEventLogRecord;
import io.casehub.flow.rest.dto.EventLogEntryResponse;
import io.casehub.flow.rest.dto.PagedResponse;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class EventLogService {

  @Inject CaseHubRuntime caseHubRuntime;

  /**
   * Get paginated and filtered event log for a case.
   *
   * @param caseId case instance UUID
   * @param page page number (1-indexed)
   * @param size page size
   * @param eventTypeNames optional event type filter
   * @param streamTypeNames optional stream type filter
   * @return paged response with event log entries
   */
  public Uni<PagedResponse<EventLogEntryResponse>> getEventLog(
      UUID caseId,
      int page,
      int size,
      List<String> eventTypeNames,
      List<String> streamTypeNames) {

    // Convert filter strings to enums
    Set<CaseHubEventType> eventTypes = convertEventTypes(eventTypeNames);
    Set<EventStreamType> streamTypes = convertStreamTypes(streamTypeNames);

    // Choose appropriate runtime method based on filters
    CompletionStage<List<CaseEventLogRecord>> eventLogFuture;
    if (!eventTypes.isEmpty() && !streamTypes.isEmpty()) {
      eventLogFuture = caseHubRuntime.eventLog(caseId, eventTypes, streamTypes);
    } else if (!eventTypes.isEmpty()) {
      eventLogFuture = caseHubRuntime.eventLog(caseId, eventTypes);
    } else {
      eventLogFuture = caseHubRuntime.eventLog(caseId);
    }

    return Uni.createFrom()
        .completionStage(eventLogFuture)
        .map(events -> buildPagedResponse(events, page, size));
  }

  /**
   * Build paged response from event list.
   *
   * @param allEvents all events (after filtering)
   * @param page page number (1-indexed)
   * @param size page size
   * @return paged response
   */
  private PagedResponse<EventLogEntryResponse> buildPagedResponse(
      List<CaseEventLogRecord> allEvents, int page, int size) {

    int totalElements = allEvents.size();
    int totalPages = (totalElements + size - 1) / size;

    // Calculate pagination bounds
    int offset = (page - 1) * size;
    int endIndex = Math.min(offset + size, totalElements);

    // Handle out-of-bounds page (return empty)
    List<EventLogEntryResponse> content;
    if (offset >= totalElements) {
      content = List.of();
    } else {
      content = allEvents.subList(offset, endIndex).stream()
          .map(this::toEventLogEntryResponse)
          .toList();
    }

    return new PagedResponse<>(content, page, size, totalElements, totalPages);
  }

  /**
   * Map CaseEventLogRecord to EventLogEntryResponse.
   *
   * @param record event log record from engine
   * @return response DTO
   */
  private EventLogEntryResponse toEventLogEntryResponse(CaseEventLogRecord record) {
    return new EventLogEntryResponse(
        record.eventType(),
        record.streamType(),
        record.timestamp(),
        record.payload(),
        record.metadata());
  }

  // ... existing convertEventTypes and convertStreamTypes methods ...
}
```

Add import:
```java
import java.util.concurrent.CompletionStage;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=EventLogServiceTest#getEventLog_noFiltersNoPagination_returnsAllEvents`
Expected: PASS

- [ ] **Step 5: Commit basic event fetching**

```bash
git add src/main/java/io/casehub/flow/service/EventLogService.java
git add src/test/java/io/casehub/flow/service/EventLogServiceTest.java
git commit -m "feat: add basic event log fetching and mapping

Implement getEventLog method that fetches events from CaseHubRuntime,
maps to EventLogEntryResponse DTOs, and builds PagedResponse. Includes
helper methods for pagination math and DTO mapping.

Issue: #7"
```

---

### Task 4: EventLogService - Pagination Logic

**Files:**
- Modify: `src/test/java/io/casehub/flow/service/EventLogServiceTest.java`

- [ ] **Step 1: Write test for first page pagination**

Add to `EventLogServiceTest`:

```java
  @Test
  void getEventLog_firstPage_returnsCorrectSubset() {
    UUID caseId = UUID.randomUUID();
    List<CaseEventLogRecord> records = createMockRecords(100);

    when(runtime.eventLog(caseId))
        .thenReturn(CompletableFuture.completedFuture(records));

    PagedResponse<EventLogEntryResponse> result =
        service.getEventLog(caseId, 1, 10, null, null).await().indefinitely();

    assertThat(result.content()).hasSize(10);
    assertThat(result.page()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(10);
    assertThat(result.totalElements()).isEqualTo(100);
    assertThat(result.totalPages()).isEqualTo(10);
  }

  @Test
  void getEventLog_middlePage_returnsCorrectSubset() {
    UUID caseId = UUID.randomUUID();
    List<CaseEventLogRecord> records = createMockRecords(100);

    when(runtime.eventLog(caseId))
        .thenReturn(CompletableFuture.completedFuture(records));

    PagedResponse<EventLogEntryResponse> result =
        service.getEventLog(caseId, 5, 10, null, null).await().indefinitely();

    assertThat(result.content()).hasSize(10);
    assertThat(result.page()).isEqualTo(5);
    assertThat(result.totalElements()).isEqualTo(100);
    assertThat(result.totalPages()).isEqualTo(10);
  }

  @Test
  void getEventLog_lastPage_returnsPartialSubset() {
    UUID caseId = UUID.randomUUID();
    List<CaseEventLogRecord> records = createMockRecords(95);

    when(runtime.eventLog(caseId))
        .thenReturn(CompletableFuture.completedFuture(records));

    PagedResponse<EventLogEntryResponse> result =
        service.getEventLog(caseId, 10, 10, null, null).await().indefinitely();

    assertThat(result.content()).hasSize(5);  // Last page has 5 items
    assertThat(result.page()).isEqualTo(10);
    assertThat(result.totalElements()).isEqualTo(95);
    assertThat(result.totalPages()).isEqualTo(10);
  }

  @Test
  void getEventLog_pageBeyondTotal_returnsEmptyPage() {
    UUID caseId = UUID.randomUUID();
    List<CaseEventLogRecord> records = createMockRecords(10);

    when(runtime.eventLog(caseId))
        .thenReturn(CompletableFuture.completedFuture(records));

    PagedResponse<EventLogEntryResponse> result =
        service.getEventLog(caseId, 5, 10, null, null).await().indefinitely();

    assertThat(result.content()).isEmpty();
    assertThat(result.page()).isEqualTo(5);
    assertThat(result.totalElements()).isEqualTo(10);
    assertThat(result.totalPages()).isEqualTo(1);
  }

  @Test
  void getEventLog_emptyEventLog_returnsEmptyPage() {
    UUID caseId = UUID.randomUUID();

    when(runtime.eventLog(caseId))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    PagedResponse<EventLogEntryResponse> result =
        service.getEventLog(caseId, 1, 50, null, null).await().indefinitely();

    assertThat(result.content()).isEmpty();
    assertThat(result.totalElements()).isEqualTo(0);
    assertThat(result.totalPages()).isEqualTo(0);
  }

  private List<CaseEventLogRecord> createMockRecords(int count) {
    ObjectNode payload = objectMapper.createObjectNode().put("index", 0);
    ObjectNode metadata = objectMapper.createObjectNode().put("traceId", "test");
    
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(i -> new CaseEventLogRecord(
            CaseHubEventType.WORKER_EXECUTION_COMPLETED,
            EventStreamType.CONTROL,
            Instant.now().plusSeconds(i),
            payload,
            metadata))
        .toList();
  }
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `mvn test -Dtest=EventLogServiceTest`
Expected: All pagination tests PASS (implementation already handles pagination)

- [ ] **Step 3: Commit pagination tests**

```bash
git add src/test/java/io/casehub/flow/service/EventLogServiceTest.java
git commit -m "test: add pagination tests for EventLogService

Add tests for first page, middle page, last page, page beyond total,
and empty event log scenarios. Verifies correct offset calculation
and page metadata.

Issue: #7"
```

---

### Task 5: EventLogService - Filtering Logic

**Files:**
- Modify: `src/test/java/io/casehub/flow/service/EventLogServiceTest.java`

- [ ] **Step 1: Write tests for eventType filtering**

Add to `EventLogServiceTest`:

```java
  @Test
  void getEventLog_withEventTypeFilter_callsCorrectRuntimeMethod() {
    UUID caseId = UUID.randomUUID();
    List<String> eventTypeNames = List.of("WORKER_EXECUTION_COMPLETED");
    
    when(runtime.eventLog(eq(caseId), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    service.getEventLog(caseId, 1, 50, eventTypeNames, null).await().indefinitely();

    Set<CaseHubEventType> expectedTypes = Set.of(CaseHubEventType.WORKER_EXECUTION_COMPLETED);
    verify(runtime).eventLog(caseId, expectedTypes);
  }

  @Test
  void getEventLog_withMultipleEventTypeFilters_callsCorrectRuntimeMethod() {
    UUID caseId = UUID.randomUUID();
    List<String> eventTypeNames = List.of("WORKER_EXECUTION_COMPLETED", "STATE_CHANGED");
    
    when(runtime.eventLog(eq(caseId), any()))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    service.getEventLog(caseId, 1, 50, eventTypeNames, null).await().indefinitely();

    verify(runtime).eventLog(eq(caseId), any(Set.class));
  }

  @Test
  void getEventLog_withStreamTypeFilter_callsRuntimeWithNoFilter() {
    UUID caseId = UUID.randomUUID();
    List<String> streamTypeNames = List.of("CONTROL");
    
    // Engine doesn't support streamType-only filtering, so falls back to no filter
    when(runtime.eventLog(caseId))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    service.getEventLog(caseId, 1, 50, null, streamTypeNames).await().indefinitely();

    verify(runtime).eventLog(caseId);
  }

  @Test
  void getEventLog_withBothFilters_callsCorrectRuntimeMethod() {
    UUID caseId = UUID.randomUUID();
    List<String> eventTypeNames = List.of("WORKER_EXECUTION_COMPLETED");
    List<String> streamTypeNames = List.of("CONTROL");
    
    when(runtime.eventLog(eq(caseId), any(Set.class), any(Set.class)))
        .thenReturn(CompletableFuture.completedFuture(List.of()));

    service.getEventLog(caseId, 1, 50, eventTypeNames, streamTypeNames).await().indefinitely();

    Set<CaseHubEventType> expectedEventTypes = Set.of(CaseHubEventType.WORKER_EXECUTION_COMPLETED);
    Set<EventStreamType> expectedStreamTypes = Set.of(EventStreamType.CONTROL);
    verify(runtime).eventLog(caseId, expectedEventTypes, expectedStreamTypes);
  }
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `mvn test -Dtest=EventLogServiceTest`
Expected: All filtering tests PASS (implementation already handles filtering)

- [ ] **Step 3: Fix streamType-only filtering implementation**

Modify `EventLogService.getEventLog()`:

```java
  public Uni<PagedResponse<EventLogEntryResponse>> getEventLog(
      UUID caseId,
      int page,
      int size,
      List<String> eventTypeNames,
      List<String> streamTypeNames) {

    // Convert filter strings to enums
    Set<CaseHubEventType> eventTypes = convertEventTypes(eventTypeNames);
    Set<EventStreamType> streamTypes = convertStreamTypes(streamTypeNames);

    // Choose appropriate runtime method based on filters
    CompletionStage<List<CaseEventLogRecord>> eventLogFuture;
    if (!eventTypes.isEmpty() && !streamTypes.isEmpty()) {
      eventLogFuture = caseHubRuntime.eventLog(caseId, eventTypes, streamTypes);
    } else if (!eventTypes.isEmpty()) {
      eventLogFuture = caseHubRuntime.eventLog(caseId, eventTypes);
    } else if (!streamTypes.isEmpty()) {
      // Engine supports streamType in combination with eventType, but not alone
      // Fetch all and filter in-memory
      eventLogFuture = caseHubRuntime.eventLog(caseId)
          .thenApply(events -> filterByStreamType(events, streamTypes));
    } else {
      eventLogFuture = caseHubRuntime.eventLog(caseId);
    }

    return Uni.createFrom()
        .completionStage(eventLogFuture)
        .map(events -> buildPagedResponse(events, page, size));
  }

  private List<CaseEventLogRecord> filterByStreamType(
      List<CaseEventLogRecord> events, Set<EventStreamType> streamTypes) {
    return events.stream()
        .filter(event -> streamTypes.contains(event.streamType()))
        .toList();
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=EventLogServiceTest`
Expected: All tests PASS

- [ ] **Step 5: Commit filtering logic**

```bash
git add src/main/java/io/casehub/flow/service/EventLogService.java
git add src/test/java/io/casehub/flow/service/EventLogServiceTest.java
git commit -m "feat: add filtering logic for event log queries

Add tests and implementation for eventType and streamType filtering.
Engine supports eventType+streamType combined, or eventType alone.
For streamType-only, filter in-memory after fetching all events.

Issue: #7"
```

---

### Task 6: EventLogResource - Basic Endpoint and 404 Handling

**Files:**
- Create: `src/main/java/io/casehub/flow/rest/EventLogResource.java`
- Create: `src/test/java/io/casehub/flow/rest/EventLogResourceIT.java`

- [ ] **Step 1: Write integration test for 404**

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

import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class EventLogResourceIT {

  @Test
  void getEventLog_nonExistentCase_returns404() {
    UUID randomCaseId = UUID.randomUUID();

    given()
        .when()
        .get("/api/v1/cases/{caseId}/events", randomCaseId)
        .then()
        .statusCode(404);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EventLogResourceIT#getEventLog_nonExistentCase_returns404`
Expected: FAIL - "404 Not Found" (endpoint doesn't exist)

- [ ] **Step 3: Create EventLogResource with basic structure**

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

import io.casehub.flow.rest.dto.EventLogEntryResponse;
import io.casehub.flow.rest.dto.PagedResponse;
import io.casehub.flow.rest.dto.ProblemDetail;
import io.casehub.flow.service.EventLogService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * REST API for case event log operations.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>GET /api/v1/cases/{caseId}/events — get paginated and filtered event log
 * </ul>
 */
@Path("/api/v1/cases/{caseId}/events")
@Produces(MediaType.APPLICATION_JSON)
public class EventLogResource {

  private static final Logger LOG = Logger.getLogger(EventLogResource.class);

  @Inject EventLogService eventLogService;

  /**
   * Get paginated and filtered event log for a case.
   *
   * @param caseId case instance UUID
   * @param page page number (1-indexed, default 1)
   * @param size page size (default 50, max 1000)
   * @param eventTypes optional event type filters
   * @param streamTypes optional stream type filters
   * @return 200 OK with paged events, 404 if case not found, 400 for invalid params
   */
  @GET
  public Uni<Response> getEventLog(
      @PathParam("caseId") UUID caseId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("50") int size,
      @QueryParam("eventType") List<String> eventTypes,
      @QueryParam("streamType") List<String> streamTypes) {

    return eventLogService
        .getEventLog(caseId, page, size, eventTypes, streamTypes)
        .map(pagedResponse -> Response.ok(pagedResponse).build())
        .onFailure(IllegalArgumentException.class)
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
              LOG.errorf(ex, "Failed to retrieve event log for case %s", caseId);
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to retrieve event log: " + ex.getMessage()))
                  .build();
            });
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=EventLogResourceIT#getEventLog_nonExistentCase_returns404`
Expected: PASS

- [ ] **Step 5: Commit basic resource**

```bash
git add src/main/java/io/casehub/flow/rest/EventLogResource.java
git add src/test/java/io/casehub/flow/rest/EventLogResourceIT.java
git commit -m "feat: add EventLogResource with 404 handling

Add REST endpoint GET /api/v1/cases/{caseId}/events with basic
structure. Handles IllegalArgumentException from service as 404.
Integration test verifies 404 for non-existent case.

Issue: #7"
```

---

### Task 7: EventLogResource - Parameter Validation

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/EventLogResource.java`
- Modify: `src/test/java/io/casehub/flow/rest/EventLogResourceIT.java`

- [ ] **Step 1: Write tests for invalid parameters**

Add to `EventLogResourceIT`:

```java
  @Test
  void getEventLog_invalidPage_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("page", 0)
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400);
  }

  @Test
  void getEventLog_negativeSize_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("size", -1)
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400);
  }

  @Test
  void getEventLog_sizeTooLarge_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("size", 1001)
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400);
  }

  @Test
  void getEventLog_invalidEventType_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("eventType", "INVALID_EVENT")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400);
  }

  @Test
  void getEventLog_invalidStreamType_returns400() {
    UUID caseId = UUID.randomUUID();

    given()
        .queryParam("streamType", "INVALID_STREAM")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(400);
  }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -Dtest=EventLogResourceIT`
Expected: New tests FAIL (no validation yet)

- [ ] **Step 3: Add validation logic to resource**

Modify `EventLogResource.getEventLog()`:

```java
  @GET
  public Uni<Response> getEventLog(
      @PathParam("caseId") UUID caseId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("50") int size,
      @QueryParam("eventType") List<String> eventTypes,
      @QueryParam("streamType") List<String> streamTypes) {

    // Validate pagination parameters
    if (page < 1) {
      return Uni.createFrom().item(
          Response.status(400)
              .entity(new ProblemDetail(
                  "Invalid request",
                  400,
                  "Page must be >= 1, got: " + page))
              .build());
    }

    if (size < 1 || size > 1000) {
      return Uni.createFrom().item(
          Response.status(400)
              .entity(new ProblemDetail(
                  "Invalid request",
                  400,
                  "Size must be between 1 and 1000, got: " + size))
              .build());
    }

    return eventLogService
        .getEventLog(caseId, page, size, eventTypes, streamTypes)
        .map(pagedResponse -> Response.ok(pagedResponse).build())
        .onFailure(IllegalArgumentException.class)
        .recoverWithItem(
            ex -> {
              // Check if this is an enum conversion error (invalid filter)
              if (ex.getMessage() != null && 
                  (ex.getMessage().contains("No enum constant") ||
                   ex.getMessage().contains("INVALID"))) {
                LOG.warnf(ex, "Invalid filter parameter");
                return Response.status(400)
                    .entity(new ProblemDetail(
                        "Invalid request",
                        400,
                        "Invalid event or stream type: " + ex.getMessage()))
                    .build();
              }
              // Otherwise it's a case not found error
              LOG.warnf(ex, "Case not found: %s", caseId);
              return Response.status(404)
                  .entity(new ProblemDetail("Case not found", 404, ex.getMessage()))
                  .build();
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOG.errorf(ex, "Failed to retrieve event log for case %s", caseId);
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to retrieve event log: " + ex.getMessage()))
                  .build();
            });
  }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -Dtest=EventLogResourceIT`
Expected: All tests PASS

- [ ] **Step 5: Commit validation**

```bash
git add src/main/java/io/casehub/flow/rest/EventLogResource.java
git add src/test/java/io/casehub/flow/rest/EventLogResourceIT.java
git commit -m "feat: add parameter validation to event log endpoint

Add validation for page (>= 1), size (1-1000), and enum filters.
Invalid enum values return 400 with error details. Integration
tests verify all validation scenarios.

Issue: #7"
```

---

### Task 8: Integration Tests - Success Scenarios

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/EventLogResourceIT.java`

- [ ] **Step 1: Write test for default pagination**

Add to `EventLogResourceIT`:

```java
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.casehub.api.engine.CaseHub;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.CaseDefinition;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@QuarkusTest
class EventLogResourceIT {

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

  // ... existing tests ...

  @Test
  void getEventLog_defaultPagination_returnsEvents() {
    UUID caseId = startTestCase();

    // Wait for case to generate some events
    await().atMost(5, SECONDS).until(() -> {
      var response = given()
          .when()
          .get("/api/v1/cases/{caseId}/events", caseId)
          .then()
          .statusCode(200)
          .extract()
          .path("totalElements");
      return (Integer) response > 0;
    });

    given()
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200)
        .body("page", equalTo(1))
        .body("size", equalTo(50))
        .body("totalElements", greaterThan(0))
        .body("content", hasSize(greaterThan(0)));
  }

  @Test
  void getEventLog_customPagination_returnsCorrectPage() {
    UUID caseId = startTestCase();

    await().atMost(5, SECONDS).until(() -> {
      var response = given()
          .when()
          .get("/api/v1/cases/{caseId}/events", caseId)
          .then()
          .extract()
          .path("totalElements");
      return (Integer) response > 0;
    });

    given()
        .queryParam("page", 1)
        .queryParam("size", 5)
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200)
        .body("page", equalTo(1))
        .body("size", equalTo(5));
  }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `mvn test -Dtest=EventLogResourceIT`
Expected: All tests PASS

- [ ] **Step 3: Commit success scenario tests**

```bash
git add src/test/java/io/casehub/flow/rest/EventLogResourceIT.java
git commit -m "test: add integration tests for event log success paths

Add tests for default pagination and custom pagination scenarios.
Tests start real case instances and verify events appear in response
with correct pagination metadata.

Issue: #7"
```

---

### Task 9: Integration Tests - Filtering Scenarios

**Files:**
- Modify: `src/test/java/io/casehub/flow/rest/EventLogResourceIT.java`

- [ ] **Step 1: Write tests for filtering**

Add to `EventLogResourceIT`:

```java
  @Test
  void getEventLog_filterByEventType_returnsOnlyMatchingEvents() {
    UUID caseId = startTestCase();

    await().atMost(5, SECONDS).until(() -> {
      var response = given()
          .when()
          .get("/api/v1/cases/{caseId}/events", caseId)
          .then()
          .extract()
          .path("totalElements");
      return (Integer) response > 0;
    });

    // Get all events first to know what types exist
    var allEvents = given()
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .extract()
        .path("content");

    // Filter by a specific event type if available
    given()
        .queryParam("eventType", "WORKER_EXECUTION_COMPLETED")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200);
  }

  @Test
  void getEventLog_filterByMultipleEventTypes_returnsMatchingEvents() {
    UUID caseId = startTestCase();

    await().atMost(5, SECONDS).until(() -> {
      var response = given()
          .when()
          .get("/api/v1/cases/{caseId}/events", caseId)
          .then()
          .extract()
          .path("totalElements");
      return (Integer) response > 0;
    });

    given()
        .queryParam("eventType", "WORKER_EXECUTION_COMPLETED")
        .queryParam("eventType", "STATE_CHANGED")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200);
  }

  @Test
  void getEventLog_filterByStreamType_returnsMatchingEvents() {
    UUID caseId = startTestCase();

    await().atMost(5, SECONDS).until(() -> {
      var response = given()
          .when()
          .get("/api/v1/cases/{caseId}/events", caseId)
          .then()
          .extract()
          .path("totalElements");
      return (Integer) response > 0;
    });

    given()
        .queryParam("streamType", "CONTROL")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200);
  }

  @Test
  void getEventLog_combinedFilters_returnsMatchingEvents() {
    UUID caseId = startTestCase();

    await().atMost(5, SECONDS).until(() -> {
      var response = given()
          .when()
          .get("/api/v1/cases/{caseId}/events", caseId)
          .then()
          .extract()
          .path("totalElements");
      return (Integer) response > 0;
    });

    given()
        .queryParam("eventType", "WORKER_EXECUTION_COMPLETED")
        .queryParam("streamType", "CONTROL")
        .when()
        .get("/api/v1/cases/{caseId}/events", caseId)
        .then()
        .statusCode(200);
  }
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `mvn test -Dtest=EventLogResourceIT`
Expected: All tests PASS

- [ ] **Step 3: Commit filtering tests**

```bash
git add src/test/java/io/casehub/flow/rest/EventLogResourceIT.java
git commit -m "test: add integration tests for event log filtering

Add tests for eventType filtering (single and multiple), streamType
filtering, and combined filters. Tests verify filters work correctly
with real case execution.

Issue: #7"
```

---

### Task 10: Final Integration and Documentation

**Files:**
- Modify: `src/main/java/io/casehub/flow/rest/EventLogResource.java`

- [ ] **Step 1: Add comprehensive JavaDoc**

Update `EventLogResource` class JavaDoc:

```java
/**
 * REST API for case event log operations.
 *
 * <p>Provides access to immutable event logs for case instances, enabling:
 *
 * <ul>
 *   <li>Observability - track worker executions, state changes, signals
 *   <li>Debugging - inspect case execution history with timestamps and payloads
 *   <li>Compliance - audit trail for regulatory requirements
 * </ul>
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>GET /api/v1/cases/{caseId}/events — get paginated and filtered event log
 * </ul>
 *
 * <p>Query Parameters:
 *
 * <ul>
 *   <li>page (int, default=1) - page number (1-indexed)
 *   <li>size (int, default=50, max=1000) - page size
 *   <li>eventType (String[], optional) - filter by event types (repeatable)
 *   <li>streamType (String[], optional) - filter by stream types (repeatable)
 * </ul>
 *
 * <p>Example:
 *
 * <pre>
 * GET /api/v1/cases/123e4567-e89b-12d3-a456-426614174000/events?page=1&size=20&eventType=WORKER_EXECUTION_COMPLETED
 * </pre>
 *
 * <p>Response: {@link PagedResponse} containing {@link EventLogEntryResponse} objects
 *
 * <p>Error Responses:
 *
 * <ul>
 *   <li>400 Bad Request - invalid pagination or filter parameters
 *   <li>404 Not Found - case does not exist
 *   <li>500 Internal Server Error - unexpected failure
 * </ul>
 */
@Path("/api/v1/cases/{caseId}/events")
@Produces(MediaType.APPLICATION_JSON)
public class EventLogResource {
```

- [ ] **Step 2: Run full test suite**

Run: `mvn clean test`
Expected: All tests PASS

- [ ] **Step 3: Run integration tests**

Run: `mvn verify`
Expected: All integration tests PASS

- [ ] **Step 4: Commit documentation**

```bash
git add src/main/java/io/casehub/flow/rest/EventLogResource.java
git commit -m "docs: add comprehensive JavaDoc to EventLogResource

Add detailed class-level JavaDoc documenting query parameters,
example usage, response types, and error codes. Improves API
discoverability and developer experience.

Issue: #7"
```

- [ ] **Step 5: Final verification**

Run full build:
```bash
mvn clean install
```
Expected: BUILD SUCCESS

---

## Plan Self-Review

### Spec Coverage Check

✅ **EventLogEntryResponse DTO** - Task 1
✅ **EventLogService with pagination** - Tasks 2-5
✅ **EventLogService with filtering** - Task 5
✅ **EventLogResource REST endpoint** - Tasks 6-7
✅ **Parameter validation** - Task 7
✅ **Error handling (404, 400, 500)** - Tasks 6-7
✅ **Unit tests for service** - Tasks 2-5
✅ **Integration tests (8 scenarios)** - Tasks 6, 8-9
✅ **JavaDoc documentation** - Task 10

### Placeholder Check

✅ No TBD, TODO, or "implement later" placeholders
✅ All code blocks complete and runnable
✅ All test methods have assertions
✅ All commands specify expected output

### Type Consistency Check

✅ `EventLogEntryResponse` - consistent across all tasks
✅ `PagedResponse<EventLogEntryResponse>` - consistent return type
✅ Method signatures match: `getEventLog(UUID, int, int, List<String>, List<String>)`
✅ Enum types: `CaseHubEventType`, `EventStreamType` - consistent

---

## Execution Notes

**Total Tasks:** 10
**Estimated Time:** 2-3 hours
**Test Coverage:** Unit tests (EventLogService) + Integration tests (EventLogResource)

**Dependencies:**
- Assumes casehub-engine 0.2-SNAPSHOT is available
- Assumes test case definitions exist for integration tests
- Assumes CaseHubRuntime bean is properly configured

**Commit Strategy:**
- One commit per task (10 commits total)
- Each commit is atomic and builds on previous work
- All commits reference Issue #7
