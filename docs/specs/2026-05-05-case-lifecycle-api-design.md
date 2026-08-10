# Case Lifecycle REST API Design

**Date:** 2026-05-05  
**Issue:** #4 - Implement REST API v1 — case lifecycle endpoints  
**Status:** Design Approved  
**Author:** Claude Code + Dmitrii Tikhomirov

## Overview

Implement REST API endpoints for managing case instance lifecycle: starting cases, querying status, and retrieving context data. The API supports case definitions from three sources: CDI beans, classpath CaseHub classes, and YAML definitions.

## Requirements Summary

From issue #4:

**Endpoints:**
- `POST /api/v1/cases` — start case by namespace/name/version with initial context
- `GET /api/v1/cases/{caseId}` — get case status and metadata
- `GET /api/v1/cases/{caseId}/context` — retrieve full case context
- `GET /api/v1/cases/{caseId}/context/{path}` — query context by path (e.g., `customer.address.city`)

**Key Decisions:**
- **Synchronous start:** POST returns 200 OK after case starts (not 202 Accepted)
- **Request format:** Nested definition object `{ definition: {...}, context: {...} }`
- **Path syntax:** Dot notation with array indexing (`customer.orders[0].id`)
- **Error handling:** Fail-fast with RFC 7807 Problem Details
- **Path not found:** 404 for missing context paths

## Architecture

### Component Layers

```
┌─────────────────────────────────────────────────┐
│         REST Layer                              │
│  CaseInstanceResource                           │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│         Service Layer                           │
│  CaseDefinitionService (extended)               │
│  CaseInstanceService (new)                      │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│         Data Layer                              │
│  CaseInstanceRepository (casehub-engine)        │
│  CaseDefinitionRegistry (casehub-engine)        │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│     CaseHub Sources (3 types)                   │
│  1. CDI @ApplicationScoped CaseHub beans        │
│  2. Classpath CaseHub (CaseHubClassPathLoader)  │
│  3. YAML → CaseHub (YamlCaseDefinitionLoader)   │
└─────────────────────────────────────────────────┘
```

**Key Point:** All three sources produce `CaseHub` instances at startup. YAML definitions are marshaled into CaseHub by `YamlCaseDefinitionLoader`. This means we have a uniform interface for starting cases regardless of source.

## Components

### 1. CaseDefinitionService (Extensions)

**Purpose:** Extend existing service to provide CaseHub lookup by definition key.

**New Fields:**
```java
@Inject Instance<CaseHub> caseHubs;
private final Map<DefinitionKey, CaseHub> caseHubIndex = new ConcurrentHashMap<>();

private record DefinitionKey(String namespace, String name, String version) {}
```

**New Methods:**

```java
void indexCaseHubs(@Observes @Priority(30) StartupEvent event) {
    // Priority 30: after CaseHubClassPathLoader (20) and YamlCaseDefinitionLoader (20)
    int count = 0;
    for (CaseHub hub : caseHubs) {
        CaseDefinition def = hub.getDefinition();
        DefinitionKey key = new DefinitionKey(
            def.getNamespace(), 
            def.getName(), 
            def.getVersion()
        );
        caseHubIndex.put(key, hub);
        count++;
    }
    LOG.infof("Indexed %d CaseHub instances from all sources (CDI + classpath + YAML)", count);
}

public Uni<Optional<CaseHub>> findCaseHub(String namespace, String name, String version) {
    DefinitionKey key = new DefinitionKey(namespace, name, version);
    CaseHub hub = caseHubIndex.get(key);
    return Uni.createFrom().item(Optional.ofNullable(hub));
}
```

**Startup Event Priority:**
- `@Priority(10)` - `DefaultCaseDefinitionRegistry.onStart()` (engine, registers CDI CaseHub)
- `@Priority(20)` - `CaseHubClassPathLoader.onStart()` (flow, discovers classpath CaseHub)
- `@Priority(20)` - `YamlCaseDefinitionLoader.onStart()` (flow, marshals YAML → CaseHub)
- `@Priority(30)` - `CaseDefinitionService.indexCaseHubs()` (flow, indexes all CaseHub)

**Integration:** Keeps existing methods (`listAll()`, `findByKey()`, etc.) unchanged.

### 2. CaseInstanceService (New)

**Purpose:** Manage case instance lifecycle operations.

**File:** `src/main/java/io/casehub/flow/service/CaseInstanceService.java`

**Dependencies:**
```java
@Inject CaseDefinitionService definitionService;
@Inject CaseInstanceRepository instanceRepository;
```

**Methods:**

#### startCase
```java
public Uni<CaseInstanceResponse> startCase(StartCaseRequest request) {
    String ns = request.definition().namespace();
    String name = request.definition().name();
    String ver = request.definition().version();
    
    // 1. Validate definition exists
    return definitionService.findByKey(ns, name, ver)
        .onItem().ifNull().failWith(() -> 
            new DefinitionNotFoundException(ns, name, ver))
        
        // 2. Find CaseHub (should exist for registered definition)
        .flatMap(def -> definitionService.findCaseHub(ns, name, ver))
        .onItem().ifNull().failWith(() -> 
            new CaseHubNotFoundException(ns, name, ver))
        .map(Optional::get)
        
        // 3. Start case via CaseHub
        .flatMap(hub -> Uni.createFrom()
            .completionStage(() -> hub.startCase(request.context())))
        
        // 4. Fetch CaseInstance from repository
        .flatMap(caseId -> instanceRepository.findByUuid(caseId))
        .onItem().ifNull().failWith(() -> 
            new CaseInstanceNotFoundException("Case started but not found in repository"))
        
        // 5. Map to response DTO
        .map(this::toCaseInstanceResponse);
}
```

#### getCaseInstance
```java
public Uni<CaseInstanceResponse> getCaseInstance(UUID caseId) {
    return instanceRepository.findByUuid(caseId)
        .onItem().ifNull().failWith(() -> 
            new CaseInstanceNotFoundException(caseId))
        .map(this::toCaseInstanceResponse);
}
```

#### getCaseContext
```java
public Uni<Map<String, Object>> getCaseContext(UUID caseId) {
    return instanceRepository.findByUuid(caseId)
        .onItem().ifNull().failWith(() -> 
            new CaseInstanceNotFoundException(caseId))
        .map(instance -> instance.getCaseContext().getData());
}
```

#### getContextPath
```java
public Uni<Object> getContextPath(UUID caseId, String path) {
    return instanceRepository.findByUuid(caseId)
        .onItem().ifNull().failWith(() -> 
            new CaseInstanceNotFoundException(caseId))
        .map(instance -> {
            Object value = instance.getCaseContext().getPath(path);
            if (value == null) {
                throw new ContextPathNotFoundException(path);
            }
            return value;
        });
}
```

**Helper Method:**
```java
private CaseInstanceResponse toCaseInstanceResponse(CaseInstance instance) {
    CaseMetaModel meta = instance.getCaseMetaModel();
    return new CaseInstanceResponse(
        instance.getUuid(),
        instance.getState(),
        meta.getNamespace(),
        meta.getName(),
        meta.getVersion(),
        meta.getCreatedAt(),
        instance.getUpdatedAt() // or meta.getCreatedAt() if no updatedAt field
    );
}
```

### 3. DTOs

**File:** `src/main/java/io/casehub/flow/rest/dto/` (new package)

#### StartCaseRequest
```java
public record StartCaseRequest(
    CaseDefinitionRef definition,
    Map<String, Object> context
) {
    public record CaseDefinitionRef(
        String namespace,
        String name,
        String version
    ) {}
}
```

**Validation:**
- `definition.namespace` - required, non-blank
- `definition.name` - required, non-blank
- `definition.version` - required, non-blank
- `context` - optional, defaults to empty map if null

#### CaseInstanceResponse
```java
public record CaseInstanceResponse(
    UUID caseId,
    CaseStatus status,
    String namespace,
    String name,
    String version,
    Instant createdAt,
    Instant updatedAt
) {}
```

**Fields:**
- `caseId` - unique case instance identifier
- `status` - enum: RUNNING, WAITING, SUSPENDED, COMPLETED, FAULTED, CANCELLED
- `namespace`, `name`, `version` - case definition key
- `createdAt` - case instance creation timestamp
- `updatedAt` - last update timestamp

### 4. REST Resource - CaseInstanceResource

**File:** `src/main/java/io/casehub/flow/rest/CaseInstanceResource.java`

**Path:** `/api/v1/cases`

```java
@Path("/api/v1/cases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaseInstanceResource {
    
    @Inject CaseInstanceService caseInstanceService;
    
    @POST
    public Uni<Response> startCase(StartCaseRequest request) {
        // Validation
        if (request == null || request.definition() == null) {
            return Uni.createFrom().item(
                Response.status(400)
                    .entity(new ProblemDetail(
                        "Invalid request",
                        400,
                        "Request body and definition are required"))
                    .build());
        }
        
        return caseInstanceService.startCase(request)
            .map(response -> Response.ok(response).build())
            .onFailure(DefinitionNotFoundException.class)
                .recoverWithItem(ex -> 
                    Response.status(404)
                        .entity(new ProblemDetail(
                            "Case definition not found",
                            404,
                            ex.getMessage()))
                        .build())
            .onFailure(ValidationException.class)
                .recoverWithItem(ex ->
                    Response.status(400)
                        .entity(new ProblemDetail(
                            "Invalid request",
                            400,
                            ex.getMessage()))
                        .build())
            .onFailure()
                .recoverWithItem(ex ->
                    Response.status(500)
                        .entity(new ProblemDetail(
                            "Internal server error",
                            500,
                            "Failed to start case instance: " + ex.getMessage()))
                        .build());
    }
    
    @GET
    @Path("/{caseId}")
    public Uni<Response> getCaseInstance(@PathParam("caseId") UUID caseId) {
        return caseInstanceService.getCaseInstance(caseId)
            .map(response -> Response.ok(response).build())
            .onFailure(CaseInstanceNotFoundException.class)
                .recoverWithItem(ex ->
                    Response.status(404)
                        .entity(new ProblemDetail(
                            "Case instance not found",
                            404,
                            ex.getMessage()))
                        .build())
            .onFailure()
                .recoverWithItem(ex ->
                    Response.status(500)
                        .entity(new ProblemDetail(
                            "Internal server error",
                            500,
                            ex.getMessage()))
                        .build());
    }
    
    @GET
    @Path("/{caseId}/context")
    public Uni<Response> getContext(@PathParam("caseId") UUID caseId) {
        return caseInstanceService.getCaseContext(caseId)
            .map(context -> Response.ok(context).build())
            .onFailure(CaseInstanceNotFoundException.class)
                .recoverWithItem(ex ->
                    Response.status(404)
                        .entity(new ProblemDetail(
                            "Case instance not found",
                            404,
                            ex.getMessage()))
                        .build())
            .onFailure()
                .recoverWithItem(ex ->
                    Response.status(500)
                        .entity(new ProblemDetail(
                            "Internal server error",
                            500,
                            ex.getMessage()))
                        .build());
    }
    
    @GET
    @Path("/{caseId}/context/{path: .*}")
    public Uni<Response> getContextPath(
            @PathParam("caseId") UUID caseId,
            @PathParam("path") String path) {
        
        return caseInstanceService.getContextPath(caseId, path)
            .map(value -> Response.ok(value).build())
            .onFailure(CaseInstanceNotFoundException.class)
                .recoverWithItem(ex ->
                    Response.status(404)
                        .entity(new ProblemDetail(
                            "Case instance not found",
                            404,
                            ex.getMessage()))
                        .build())
            .onFailure(ContextPathNotFoundException.class)
                .recoverWithItem(ex ->
                    Response.status(404)
                        .entity(new ProblemDetail(
                            "Context path not found",
                            404,
                            ex.getMessage()))
                        .build())
            .onFailure()
                .recoverWithItem(ex ->
                    Response.status(500)
                        .entity(new ProblemDetail(
                            "Internal server error",
                            500,
                            ex.getMessage()))
                        .build());
    }
}
```

**Path Parameter Regex:** `{path: .*}` captures entire path including slashes and dots.

**ProblemDetail:** Reuse existing record from `CaseDefinitionResource.java`.

### 5. Custom Exceptions

**File:** `src/main/java/io/casehub/flow/exception/` (new package)

```java
public class DefinitionNotFoundException extends RuntimeException {
    public DefinitionNotFoundException(String namespace, String name, String version) {
        super(String.format(
            "No case definition found for namespace '%s', name '%s', version '%s'",
            namespace, name, version));
    }
}

public class CaseHubNotFoundException extends RuntimeException {
    public CaseHubNotFoundException(String namespace, String name, String version) {
        super(String.format(
            "No CaseHub found for definition namespace '%s', name '%s', version '%s'",
            namespace, name, version));
    }
}

public class CaseInstanceNotFoundException extends RuntimeException {
    public CaseInstanceNotFoundException(UUID caseId) {
        super(String.format("No case instance found with id '%s'", caseId));
    }
    
    public CaseInstanceNotFoundException(String message) {
        super(message);
    }
}

public class ContextPathNotFoundException extends RuntimeException {
    public ContextPathNotFoundException(String path) {
        super(String.format("Path '%s' does not exist in case context", path));
    }
}
```

## Data Flow

### Flow 1: Start Case (All Sources)

```
POST /api/v1/cases
Body: {
  "definition": {
    "namespace": "test-api",
    "name": "Document Approval",
    "version": "1.0.0"
  },
  "context": {
    "documentId": "DOC-123",
    "submittedBy": "alice@example.com"
  }
}

↓
CaseInstanceResource.startCase(request)
  ↓
CaseInstanceService.startCase(request)
  ↓
1. definitionService.findByKey(ns, name, ver)
   → Validates definition exists in CaseDefinitionRegistry
   → If not found: throw DefinitionNotFoundException → 404
  ↓
2. definitionService.findCaseHub(ns, name, ver)
   → Searches caseHubIndex
   → Returns Optional<CaseHub>
   → If empty: throw CaseHubNotFoundException → 500 (should not happen)
  ↓
3. caseHub.startCase(context)
   → CompletionStage<UUID>
   → Converts to Uni
   → Returns caseId
  ↓
4. instanceRepository.findByUuid(caseId)
   → Retrieves CaseInstance with current state
   → If not found: throw exception → 500 (should not happen)
  ↓
5. Map CaseInstance → CaseInstanceResponse
   → Extract: uuid, status, namespace, name, version, timestamps
  ↓
6. Return 200 OK with CaseInstanceResponse

Response:
{
  "caseId": "123e4567-e89b-12d3-a456-426614174000",
  "status": "RUNNING",
  "namespace": "test-api",
  "name": "Document Approval",
  "version": "1.0.0",
  "createdAt": "2026-05-05T18:30:00Z",
  "updatedAt": "2026-05-05T18:30:00Z"
}
```

**Note:** This flow is identical for CDI beans, classpath CaseHub, and YAML definitions because all three are indexed as CaseHub instances.

### Flow 2: Get Case Instance

```
GET /api/v1/cases/123e4567-e89b-12d3-a456-426614174000

↓
CaseInstanceResource.getCaseInstance(caseId)
  ↓
CaseInstanceService.getCaseInstance(caseId)
  ↓
1. instanceRepository.findByUuid(caseId)
   → Returns CaseInstance or null
   → If null: throw CaseInstanceNotFoundException → 404
  ↓
2. Map CaseInstance → CaseInstanceResponse
  ↓
3. Return 200 OK with response

Response: (same as Flow 1)
```

### Flow 3: Get Full Context

```
GET /api/v1/cases/123e4567-e89b-12d3-a456-426614174000/context

↓
CaseInstanceResource.getContext(caseId)
  ↓
CaseInstanceService.getCaseContext(caseId)
  ↓
1. instanceRepository.findByUuid(caseId)
   → If null: throw CaseInstanceNotFoundException → 404
  ↓
2. instance.getCaseContext().getData()
   → Returns Map<String, Object>
  ↓
3. Return 200 OK with context map

Response:
{
  "documentId": "DOC-123",
  "submittedBy": "alice@example.com",
  "approvalStatus": "pending",
  "reviewers": ["bob@example.com", "charlie@example.com"]
}
```

### Flow 4: Query Context Path

```
GET /api/v1/cases/123e4567-e89b-12d3-a456-426614174000/context/reviewers[0]

↓
CaseInstanceResource.getContextPath(caseId, "reviewers[0]")
  ↓
CaseInstanceService.getContextPath(caseId, "reviewers[0]")
  ↓
1. instanceRepository.findByUuid(caseId)
   → If null: throw CaseInstanceNotFoundException → 404
  ↓
2. instance.getCaseContext().getPath("reviewers[0]")
   → CaseContext uses dot notation parser
   → Returns Object or null
   → If null: throw ContextPathNotFoundException → 404
  ↓
3. Return 200 OK with value

Response:
"bob@example.com"
```

**Path Examples:**
- `customer.name` → simple property
- `customer.address.city` → nested property
- `orders[0].id` → array index
- `metadata.tags[2]` → nested array

## Error Handling

### HTTP Status Codes

| Status | Scenario | Example |
|--------|----------|---------|
| 200 OK | Success | Case started, instance found, context retrieved |
| 400 Bad Request | Invalid request | Missing fields, malformed JSON |
| 404 Not Found | Resource not found | Definition not found, case not found, path not found |
| 500 Internal Server Error | Server error | Repository failure, engine exception |

### RFC 7807 Problem Details

**Response Format:**
```java
public record ProblemDetail(
    String title,    // Short, human-readable summary
    int status,      // HTTP status code
    String detail    // Human-readable explanation specific to this occurrence
) {}
```

**Examples:**

**Definition Not Found (404):**
```json
{
  "title": "Case definition not found",
  "status": 404,
  "detail": "No case definition found for namespace 'test-api', name 'Unknown Case', version '1.0.0'"
}
```

**Case Instance Not Found (404):**
```json
{
  "title": "Case instance not found",
  "status": 404,
  "detail": "No case instance found with id '123e4567-e89b-12d3-a456-426614174000'"
}
```

**Context Path Not Found (404):**
```json
{
  "title": "Context path not found",
  "status": 404,
  "detail": "Path 'customer.invalidField' does not exist in case context"
}
```

**Invalid Request (400):**
```json
{
  "title": "Invalid request",
  "status": 400,
  "detail": "Request body and definition are required"
}
```

**Internal Server Error (500):**
```json
{
  "title": "Internal server error",
  "status": 500,
  "detail": "Failed to start case instance: <exception message>"
}
```

### Error Handling Strategy

**Fail-Fast Principle:**
- Validate early (definition exists before attempting start)
- Return clear, specific error messages
- Don't create partial state (no FAULTED cases for missing definitions)

**Reactive Error Transformation:**
```java
service.operation()
    .onFailure(SpecificException.class)
        .recoverWithItem(ex -> Response.status(code).entity(problem).build())
    .onFailure()
        .recoverWithItem(ex -> Response.status(500).entity(problem).build())
```

## Testing Strategy

### Unit Tests

**CaseDefinitionServiceTest** (extend existing test):
- `indexCaseHubs_indexesAllSources()` - verify CDI + classpath + YAML indexed
- `findCaseHub_returnsCaseHub_forCdiBeans()` - lookup CDI bean by key
- `findCaseHub_returnsCaseHub_forClasspathCaseHub()` - lookup classpath by key
- `findCaseHub_returnsCaseHub_forYamlDefinition()` - lookup YAML-derived CaseHub
- `findCaseHub_returnsEmpty_whenNotFound()` - non-existent key

**CaseInstanceServiceTest** (new):
- `startCase_withCdiBeanDefinition_startsSuccessfully()` - CDI CaseHub source
- `startCase_withClasspathCaseHub_startsSuccessfully()` - classpath source
- `startCase_withYamlDefinition_startsSuccessfully()` - YAML source
- `startCase_throwsDefinitionNotFoundException_whenDefinitionNotFound()` - validation
- `startCase_throwsCaseHubNotFoundException_whenCaseHubNotFound()` - edge case
- `getCaseInstance_returnsInstance_whenExists()` - fetch by UUID
- `getCaseInstance_throwsNotFoundException_whenNotFound()` - not found
- `getCaseContext_returnsFullContext_whenExists()` - full context map
- `getContextPath_returnsValue_whenPathExists()` - dot notation
- `getContextPath_withArrayIndex_returnsValue()` - array indexing
- `getContextPath_throwsNotFoundException_whenPathNotFound()` - invalid path
- `getContextPath_throwsNotFoundException_whenCaseNotFound()` - invalid case

**Mock Strategy:**
- Mock `CaseDefinitionService` to return known definitions/CaseHub
- Mock `CaseInstanceRepository` to return test CaseInstance objects
- Mock `CaseHub.startCase()` to return CompletionStage with UUID

### Integration Tests

**CaseInstanceResourceTest** (new):
- `startCase_cdiBeanDefinition_returns200WithCorrectStatus()` - full POST flow with CDI
- `startCase_classpathCaseHub_returns200()` - full POST flow with classpath
- `startCase_yamlDefinition_returns200()` - full POST flow with YAML
- `startCase_definitionNotFound_returns404WithProblemDetail()` - error case
- `startCase_invalidRequest_returns400()` - missing fields
- `getCaseInstance_returns200WithCorrectData()` - GET status
- `getCaseInstance_notFound_returns404()` - non-existent UUID
- `getContext_returns200WithFullContext()` - GET full context
- `getContextPath_simpleProperty_returns200()` - `customer.name`
- `getContextPath_nestedProperty_returns200()` - `customer.address.city`
- `getContextPath_arrayIndex_returns200()` - `orders[0].total`
- `getContextPath_caseNotFound_returns404()` - invalid case
- `getContextPath_pathNotFound_returns404()` - invalid path
- `fullLifecycle_startThenQueryContext()` - POST → GET /context → GET /context/path

**Test Data:**
Reuse existing CaseHub test definitions from `CaseDefinitionResourceTest`:
- **CDI beans:** Document Approval v1.0.0, v2.0.0, Invoice Processing v1.0.0
- **Classpath:** Classpath Only Case v1.0.0
- **YAML:** YAML Test Case v1.0.0, valid/minimal.yaml, valid/complete.yaml

**Assertions:**
- HTTP status codes
- Response body structure (DTO fields)
- Problem detail format for errors
- Context path queries return correct values
- All three sources work uniformly

### Coverage Goals
- **Unit tests:** 90%+ code coverage
- **Integration tests:** Happy path + all error scenarios
- **Cross-source validation:** Each test category covers CDI, classpath, and YAML

## Implementation Notes

### Dot Notation Path Parsing

**Delegation to CaseContext:**
The `CaseContext` interface already provides `getPath(String path)` which supports:
- Simple properties: `customer.name`
- Nested properties: `customer.address.city`
- Array indexing: `orders[0].id`

We don't need to implement path parsing — just delegate to `CaseContext.getPath()`.

**Null Handling:**
- If path doesn't exist, `getPath()` returns `null`
- Service layer throws `ContextPathNotFoundException`
- Resource layer maps to 404 with Problem Detail

### CompletionStage to Uni Conversion

**Pattern:**
```java
Uni.createFrom().completionStage(() -> caseHub.startCase(context))
```

**Why Supplier?**
- Lazy evaluation: CompletionStage created only when Uni is subscribed
- Proper reactive chain composition

### Startup Event Priority

**Ensure correct order:**
```java
@Observes @Priority(30) StartupEvent event
```

This ensures:
1. Engine registers CDI CaseHub (priority 10)
2. Loaders discover classpath/YAML and register (priority 20)
3. CaseDefinitionService indexes all CaseHub (priority 30)

### Reactive Chaining Pattern

**Consistent style:**
```java
return operation1()
    .flatMap(result1 -> operation2(result1))
    .map(result2 -> transform(result2))
    .onFailure(SpecificException.class)
        .recoverWithItem(ex -> fallback(ex));
```

**When to use `.flatMap()` vs `.map()`:**
- `.flatMap()` when next operation returns `Uni<T>`
- `.map()` when next operation returns `T`

## Implementation Checklist

- [ ] Create `io.casehub.flow.exception` package with custom exceptions
- [ ] Create `io.casehub.flow.rest.dto` package with request/response DTOs
- [ ] Extend `CaseDefinitionService` with `indexCaseHubs()` and `findCaseHub()`
- [ ] Implement `CaseInstanceService` with all lifecycle methods
- [ ] Implement `CaseInstanceResource` with all endpoints
- [ ] Write unit tests for `CaseDefinitionService` extensions
- [ ] Write unit tests for `CaseInstanceService`
- [ ] Write integration tests for `CaseInstanceResource`
- [ ] Verify all three sources (CDI, classpath, YAML) in integration tests
- [ ] Test error scenarios (404, 400, 500)
- [ ] Test context path queries with dot notation and array indexing
- [ ] Update API documentation (if exists)

## Success Criteria

All acceptance criteria from issue #4 met:

- [x] Design: POST /cases validates definition exists and starts case instance
- [x] Design: Response includes caseId, status, namespace, name, version, timestamps
- [x] Design: GET /cases/{caseId} returns current status and metadata
- [x] Design: GET context endpoints return 404 if caseId doesn't exist
- [x] Design: Context path query supports dot notation and array indexing
- [x] Design: Error responses follow RFC 7807 format
- [x] Design: All three sources (CDI, classpath, YAML) handled uniformly
- [ ] Implementation: Integration tests cover full lifecycle
- [ ] Implementation: All tests pass

## Open Questions

None. Design approved and ready for implementation.

## References

- Issue #4: https://github.com/treblereel/casehub-flow/issues/4
- Issue #3 (definition loading): Commit 3a82ada
- CaseHub API: `io.casehub.api.engine.CaseHub`
- CaseContext API: `io.casehub.api.context.CaseContext`
- RFC 7807 Problem Details: https://tools.ietf.org/html/rfc7807
