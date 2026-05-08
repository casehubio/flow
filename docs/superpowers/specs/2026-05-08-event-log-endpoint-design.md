# Event Log / Audit Trail REST API Endpoint

**Issue:** [#7](https://github.com/casehubio/flow/issues/7)  
**Epic:** [#1](https://github.com/casehubio/flow/issues/1) - Build production-ready REST microservice  
**Date:** 2026-05-08

## Overview

Implement `GET /api/v1/cases/{caseId}/events` endpoint to expose immutable event log for case instances. This provides observability, debugging capabilities, and compliance audit trail by surfacing the event stream from casehub-engine.

## Context

Part of building a production-ready REST microservice on top of casehub-engine. The engine already provides event log retrieval through `CaseHubRuntime.eventLog()` methods with filtering capabilities. This design wraps those methods with a paginated REST API.

## Architecture & Components

### New Components

#### 1. EventLogResource
**Package:** `io.casehub.flow.rest.EventLogResource`

REST controller for event log endpoint.
- Path: `GET /api/v1/cases/{caseId}/events`
- Query params: `page`, `size`, `eventType` (multiple), `streamType` (multiple)
- Returns: `PagedResponse<EventLogEntryResponse>`
- Error handling: 404 (case not found), 400 (invalid params), 500 (internal error)

#### 2. EventLogService
**Package:** `io.casehub.flow.service.EventLogService`

Business logic for event log operations.
- Calls `CaseHubRuntime.eventLog()` with appropriate filters
- Applies in-memory pagination (offset/limit)
- Converts String query params to enum types
- Maps `CaseEventLogRecord` → `EventLogEntryResponse`

#### 3. EventLogEntryResponse
**Package:** `io.casehub.flow.rest.dto.EventLogEntryResponse`

DTO record representing a single event log entry. Fields map directly from `CaseEventLogRecord`:
- `CaseHubEventType eventType` - type of event
- `EventStreamType streamType` - stream classification
- `Instant timestamp` - when event occurred
- `JsonNode payload` - event data
- `JsonNode metadata` - event metadata (traceId, etc.)

### Existing Components (Reused)

- **PagedResponse<T>** - generic pagination wrapper
- **ProblemDetail** - RFC 7807 error responses
- **CaseHubRuntime** - source of event log data from engine

## API Contract

### Endpoint

```
GET /api/v1/cases/{caseId}/events
```

### Path Parameters

- `caseId` (UUID, required) - case instance identifier

### Query Parameters

- `page` (int, optional, default=1) - page number (1-indexed)
- `size` (int, optional, default=50) - page size (max 1000)
- `eventType` (String[], optional) - filter by event types, repeatable
  - Example: `?eventType=WORKER_EXECUTION_COMPLETED&eventType=STATE_CHANGED`
- `streamType` (String[], optional) - filter by stream types, repeatable
  - Example: `?streamType=CONTROL&streamType=DATA`

### Success Response (200 OK)

```json
{
  "content": [
    {
      "eventType": "WORKER_EXECUTION_COMPLETED",
      "streamType": "CONTROL",
      "timestamp": "2026-05-08T10:30:15Z",
      "payload": { "workerId": "xyz", "result": "success" },
      "metadata": { "traceId": "abc-123" }
    }
  ],
  "page": 1,
  "size": 50,
  "totalElements": 247,
  "totalPages": 5
}
```

### Error Responses

- **404 Not Found** - case not found (uses `ProblemDetail`)
- **400 Bad Request** - invalid query parameters (invalid page/size, unknown event/stream type)
- **500 Internal Server Error** - unexpected failure

### Ordering

Events ordered by sequence number ascending (as returned from engine).

### Validation Rules

- `page >= 1`
- `size >= 1 && size <= 1000`
- `eventType` values must be valid `CaseHubEventType` enum values
- `streamType` values must be valid `EventStreamType` enum values

## Data Flow & Implementation Logic

### Request Flow

1. **EventLogResource receives request**
   - Validates path parameter `caseId`
   - Parses query parameters: `page`, `size`, `eventType[]`, `streamType[]`
   - Validates query params (page >= 1, size in [1, 1000])
   - Calls `EventLogService.getEventLog(...)`

2. **EventLogService processes request**
   - Converts `eventType` strings → `Set<CaseHubEventType>` enum
   - Converts `streamType` strings → `Set<EventStreamType>` enum
   - Calls appropriate `CaseHubRuntime.eventLog()` method:
     - If both filters present → `eventLog(caseId, eventTypes, streamTypes)`
     - If only eventTypes → `eventLog(caseId, eventTypes)`
     - If no filters → `eventLog(caseId)`
   - Waits for `CompletionStage<List<CaseEventLogRecord>>`
   - Applies in-memory pagination:
     - Calculate offset: `(page - 1) * size`
     - Slice list: `events.subList(offset, min(offset + size, total))`
   - Maps each `CaseEventLogRecord` → `EventLogEntryResponse`
   - Builds `PagedResponse<EventLogEntryResponse>`:
     - `content` = mapped events for current page
     - `page` = requested page
     - `size` = requested size
     - `totalElements` = total count before pagination
     - `totalPages` = `(totalElements + size - 1) / size`

3. **EventLogResource returns response**
   - Success: `Response.ok(pagedResponse)`
   - Case not found: catch `IllegalArgumentException` → 404 with `ProblemDetail`
   - Invalid params: validate early → 400 with `ProblemDetail`
   - Other errors: → 500 with `ProblemDetail`

### Error Handling Strategy

- `IllegalArgumentException` from engine → 404 (case not found)
- Invalid enum values in query params → 400 (bad request)
- Pagination out of bounds (page > totalPages) → return empty page (not error)
- All other exceptions → 500 with generic error message

## Testing Strategy

### Unit Tests (EventLogServiceTest)

Mock `CaseHubRuntime.eventLog()` responses and test:

**Pagination Logic:**
- First page, middle page, last page
- Page beyond totalPages (empty result)
- Different page sizes (1, 10, 50, 100, 1000)
- Edge cases: empty event log, single event

**Filtering:**
- Only eventType filter (single value)
- Only eventType filter (multiple values)
- Only streamType filter (single value)
- Only streamType filter (multiple values)
- Both filters combined
- No filters (all events)

**Enum Conversion:**
- Valid enum values
- Invalid enum values (expect IllegalArgumentException)
- Case sensitivity handling

**Error Handling:**
- IllegalArgumentException (case not found)
- CompletionStage failures

### Integration Tests (EventLogResourceIT)

Following pattern from `CaseControlResourceIT`:

**Test Cases:**

1. **GET events for non-existent case**
   - Random UUID
   - Verify 404 response with ProblemDetail

2. **GET events with default pagination**
   - Start test case
   - Execute workers (generate events)
   - GET `/api/v1/cases/{caseId}/events`
   - Verify 200 response
   - Verify events in chronological order
   - Verify pagination metadata (page=1, size=50, totalElements, totalPages)

3. **GET events with custom pagination**
   - Start case with many events (>50)
   - GET page 1 with size=10
   - GET page 2 with size=10
   - Verify no overlap between pages
   - Verify correct ordering across pages

4. **Filter by single eventType**
   - Start case, generate mixed events
   - GET with `?eventType=WORKER_EXECUTION_COMPLETED`
   - Verify only matching events returned
   - Verify totalElements reflects filtered count

5. **Filter by multiple eventTypes**
   - GET with `?eventType=TYPE1&eventType=TYPE2`
   - Verify only TYPE1 and TYPE2 events returned
   - Verify union of both types

6. **Filter by streamType**
   - GET with `?streamType=CONTROL`
   - Verify only CONTROL stream events

7. **Combined filters (eventType + streamType)**
   - GET with both filters
   - Verify intersection of filters (AND logic)

8. **Invalid query parameters**
   - Invalid page (0, negative)
   - Invalid size (0, >1000)
   - Invalid eventType enum value
   - Invalid streamType enum value
   - Verify 400 responses with ProblemDetail

**Test Data:**
- Reuse existing test case definitions from `CaseControlResourceIT`
- May need helper methods to trigger specific event types for filtering tests

## File Structure

```
src/main/java/io/casehub/flow/
├── rest/
│   ├── EventLogResource.java          (NEW)
│   └── dto/
│       └── EventLogEntryResponse.java  (NEW)
└── service/
    └── EventLogService.java            (NEW)

src/test/java/io/casehub/flow/
├── rest/
│   └── EventLogResourceIT.java        (NEW)
└── service/
    └── EventLogServiceTest.java       (NEW)
```

## Implementation Checklist

### 1. DTOs
- [ ] Create `EventLogEntryResponse` record with 5 fields from `CaseEventLogRecord`
- [ ] Add Jackson annotations if needed for JsonNode serialization
- [ ] Add validation annotations (@NotNull, etc.)

### 2. Service Layer
- [ ] Create `EventLogService` with `@ApplicationScoped`
- [ ] Inject `CaseHubRuntime`
- [ ] Implement `getEventLog()` method with pagination + filtering
- [ ] Enum conversion helpers (String → CaseHubEventType/EventStreamType)
- [ ] Pagination math: offset calculation, subList slicing, bounds checking
- [ ] Mapping: `CaseEventLogRecord` → `EventLogEntryResponse`
- [ ] Error handling for invalid enums, missing cases

### 3. REST Layer
- [ ] Create `EventLogResource` with JAX-RS annotations
- [ ] Path: `@Path("/api/v1/cases/{caseId}/events")`
- [ ] Query params: `@QueryParam` for page, size, eventType[], streamType[]
- [ ] Inject `EventLogService`
- [ ] Validation logic for query params (page >= 1, size in [1, 1000])
- [ ] Error handling: 404, 400, 500 with `ProblemDetail`
- [ ] Return `Uni<Response>` for reactive handling

### 4. Unit Tests
- [ ] `EventLogServiceTest` with 10+ test cases
- [ ] Mock `CaseHubRuntime`
- [ ] Test all pagination scenarios
- [ ] Test all filtering combinations
- [ ] Test enum conversion edge cases
- [ ] Test error handling paths

### 5. Integration Tests
- [ ] `EventLogResourceIT` with 8 test cases
- [ ] Use `@QuarkusTest`
- [ ] Follow pattern from `CaseControlResourceIT`
- [ ] Test happy path + error cases
- [ ] Verify REST contract (status codes, response structure)
- [ ] Test actual engine integration (events appear after worker execution)

### 6. Documentation Updates
- [ ] Update API documentation (if exists)
- [ ] Update OpenAPI spec (if exists)
- [ ] Add JavaDoc to public methods

## Dependencies

No new dependencies required. Uses existing:
- `CaseHubRuntime` (casehub-engine)
- `PagedResponse<T>` (existing DTO)
- `ProblemDetail` (existing DTO)
- Jackson for JSON serialization (JsonNode)
- Quarkus REST (JAX-RS)
- SmallRye Mutiny (Uni)

## Design Decisions

### Why separate EventLogResource instead of extending CaseInstanceResource?
- **Separation of Concerns**: Event log is a distinct feature with its own lifecycle
- **Scalability**: Event log may grow to include additional endpoints (streaming, subscriptions)
- **Testability**: Isolated testing without affecting case instance tests
- **Modularity**: Easier to maintain and extend independently

### Why in-memory pagination instead of pushing to engine?
- Engine's `eventLog()` methods already return filtered lists (database-level filtering)
- Pagination at REST layer is simpler for v1
- Max size limit (1000) prevents memory abuse
- Future optimization: add pagination to engine if event logs grow very large

### Why offset/limit instead of cursor-based pagination?
- Simpler implementation using existing `PagedResponse<T>`
- Matches existing API patterns in codebase
- Event log is ordered by sequence number (stable ordering)
- Cursor-based can be added later if needed for streaming use cases

### Why direct JsonNode mapping instead of typed payload?
- `CaseEventLogRecord` uses `JsonNode` - preserves flexibility
- Different event types have different payload structures
- Avoids complex polymorphic deserialization
- Clients can parse payload based on eventType

## Acceptance Criteria Mapping

From issue #7:

- [x] Returns chronological list of all events (worker executions, state transitions, signals received)
  - ✅ Engine provides events ordered by sequence number
- [x] Each event includes: timestamp, event type, actor/worker name, context diff, trace ID
  - ✅ Fields available in `CaseEventLogRecord`: timestamp, eventType, payload (contains worker/actor data), metadata (contains traceId)
- [x] Supports pagination (offset/limit or cursor-based)
  - ✅ Offset/limit via `page` and `size` query params
- [x] Supports filtering by event type
  - ✅ `eventType` query param, multiple values supported
- [x] Returns 404 if caseId doesn't exist
  - ✅ Catches `IllegalArgumentException` from engine → 404
- [x] Error responses follow RFC 7807 format
  - ✅ Uses existing `ProblemDetail` DTO
- [x] Integration tests verify events appear after worker execution, signal processing, state changes
  - ✅ Test strategy includes 8 integration tests covering these scenarios

## Future Enhancements (Out of Scope for v1)

- Cursor-based pagination for streaming scenarios
- WebSocket/SSE streaming of new events
- Pagination at engine level (database-level LIMIT/OFFSET)
- Event log retention policies
- Event search by payload content (full-text search)
- Event replay capabilities
- Export event log to external systems (S3, analytics)
