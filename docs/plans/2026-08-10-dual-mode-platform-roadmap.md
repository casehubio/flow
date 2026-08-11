# Dual-Mode Platform Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** casehubio/parent#405 — Dual-Mode Platform: Embedded + Centralized (GraphQL + MCP)
**Issue group:** Spans casehub-ledger, casehub-engine, casehub-work, casehub-qhorus, casehub-platform, scaffold
**Slot:** 110 (all repos cloned)

**Goal:** Enable the CaseHub platform to operate in two deployment models — embedded (library) and centralized (CaseHub server via scaffold) — with four generated presentation layers (REST, GraphQL, MCP, typed client) from a single annotated service interface.

**Architecture:** Four phases. Phase 1 fixes the ledger tier violation. Phase 2 builds the `@PlatformService` annotation model and Quarkus build-time code generator that produces REST endpoints, GraphQL resolvers, MCP model providers, and typed client from annotated service interfaces. Phase 3 annotates all foundation service interfaces and retires hand-written REST modules. Phase 4 adds SPI callback registration for remote extension. Phases 1 and 2 are independent and can proceed in parallel.

**Tech Stack:** Quarkus 3.32+, SmallRye GraphQL (MicroProfile GraphQL), MCP SDK, PostgreSQL JSONB, Jackson, virtual threads (Java 21)

## Global Constraints

- Pre-release platform: breaking changes cost nothing. Fix the design.
- Module tier structure: api/ is pure Java (no JPA, no Quarkus runtime). JPA lives in runtime/ or persistence-* modules only.
- Virtual threads: no `Uni<T>` in SPIs. Blocking interfaces only (ADR-0005).
- Flyway conventions: per-module migration ranges per `docs/platform/persistence.md`.
- All commits reference an issue.
- IntelliJ MCP mandatory for .java file operations.

## Core Design: One Source, Four Outputs

```
@PlatformService(domain = "engine")
public interface CaseHubRuntime {
    @Mutation UUID startCase(CaseDefinition def, Object inputData);
    @Query Object query(UUID caseId, String path);
    @Mutation void suspendCase(UUID caseId);
}

        │ Quarkus build-time generator
        ├──→ REST      JAX-RS resource class         (generated)
        ├──→ GraphQL   SmallRye resolver class        (generated)
        ├──→ MCP       ModelProvider + action catalog  (generated)
        └──→ Client    typed Java interface            (generated from GraphQL)
```

**Annotations live in `casehub-platform-api`** (pure Java, Tier 1 — annotation types only, no runtime).

**Generator lives in a Quarkus deployment module** — processes annotations at build time, generates source classes.

**MCP hierarchy:** Two hops. `domain` attribute is the only hierarchy input. Multiple interfaces with the same `domain` merge into one group. Agent calls `casehub_model()` → domain list with summaries. `casehub_model("engine")` → all engine operations with parameters. `casehub_action(domain, op, params)` → executes.

---

## Phase 1: Ledger Tier Violation Fix

**Goal:** Strip JPA annotations from `casehub-ledger-api` to restore tier purity. Move JPA field mappings to `runtime/` via `orm.xml`. Add `domainData` extension field for remote entry support in centralized mode. Preserve JOINED inheritance, typed domain subtypes, and the `domainContentBytes()` build-time enforcement mechanism.

**Repo:** casehub-ledger
**Issue:** casehubio/ledger#189

### Task 1.1: Strip JPA annotations from LedgerEntry and LedgerSupplement in api/

**Files:**
- Modify: `api/src/main/java/io/casehub/ledger/api/model/LedgerEntry.java`
- Modify: `api/src/main/java/io/casehub/ledger/api/model/supplement/LedgerSupplement.java`
- Test: `api/src/test/java/io/casehub/ledger/api/model/LedgerEntryTest.java`

**Interfaces:**
- Produces: `LedgerEntry` as a plain abstract Java class — no `@MappedSuperclass`, no `@Column`, no `@Id`, no `@Enumerated`, no `@Transient`. All fields remain with identical names and types.
- Produces: `LedgerSupplement` as a plain abstract Java class — no `@MappedSuperclass`.
- Unchanged: `canonicalBytes()`, `domainContentBytes()`, `attach()`, all supplement helpers, `AuditRecord`, `OutcomeRecord`.

- [ ] Strip JPA annotations from LedgerEntry
- [ ] Strip `@MappedSuperclass` from LedgerSupplement
- [ ] Verify api/ module compiles with zero `jakarta.persistence` imports
- [ ] Run `mvn --batch-mode install` on api/ module
- [ ] Commit: `refactor(ledger): strip JPA annotations from api/ — fix tier violation`

### Task 1.2: Define JPA mappings via orm.xml in runtime/

**Files:**
- Create: `runtime/src/main/resources/META-INF/orm.xml`
- Test: existing runtime tests, consumer-compat-test

**Interfaces:**
- Consumes: `LedgerEntry` (plain Java from Task 1.1)
- Produces: orm.xml that declares `LedgerEntry` as `<mapped-superclass>` with column mappings for all fields
- Unchanged: `JpaLedgerEntry` (`@Entity`, `@Inheritance(JOINED)`), all entity subtypes

- [ ] Create orm.xml with LedgerEntry and LedgerSupplement mapped-superclass declarations
- [ ] Run full runtime test suite
- [ ] Run consumer-compat-test module
- [ ] Commit: `refactor(ledger): orm.xml owns JPA mappings — api/ tier-pure`

### Task 1.2a: Cross-JAR orm.xml verification (GATE)

**Verify Quarkus build-time augmentation discovers orm.xml from casehub-ledger runtime/ JAR when processing entity subtypes in other repos.**

- [ ] Build and run `casehub-engine-ledger` tests (CaseLedgerEntry extends JpaLedgerEntry extends LedgerEntry)
- [ ] If PASS: proceed to Task 1.3
- [ ] If FAIL — fallback: keep `@MappedSuperclass` on LedgerEntry, strip only `@Column`/`@Id`/`@Enumerated` annotations (partial fix, still removes 14 of 15 JPA annotations)

### Task 1.3: Add domainData extension field

**Files:**
- Modify: `api/src/main/java/io/casehub/ledger/api/model/LedgerEntry.java`
- Modify: orm.xml — add `domain_data` column mapping with AttributeConverter
- Create: Flyway migration for `domain_data JSONB` column
- Create: `runtime/.../model/DomainDataConverter.java` — JPA `AttributeConverter<Map<String, Object>, String>`
- Test: canonicalBytes determinism, JPA round-trip

**Changes:**
- Add `public Map<String, Object> domainData` field to `LedgerEntry` (default: null)
- Map in orm.xml with `@Convert` to `DomainDataConverter` (Map ↔ JSONB)
- Flyway migration: `ALTER TABLE ledger_entry ADD COLUMN domain_data JSONB` + GIN index
- Update `canonicalBytes()` to include `domainData` when non-null — serialized via RFC 8785 (JCS) for deterministic hashing
- ADDITIVE: existing entries have null domainData, produce identical hashes

- [ ] Add domainData field to LedgerEntry
- [ ] Create DomainDataConverter
- [ ] Add orm.xml mapping for domainData
- [ ] Write Flyway migration + GIN index
- [ ] Write tests: null domainData = same hash as before
- [ ] Write tests: populated domainData included deterministically
- [ ] Write tests: JPA round-trip
- [ ] Commit: `feat(ledger): domainData extension field for remote entry support`

### Task 1.4: Update persistence-memory and ledger-rest

**Files:**
- Modify: `persistence-memory/` — InMemoryLedgerEntryRepository
- Modify: `rest/` — DTOs

- [ ] Update InMemoryLedgerEntryRepository for domainData
- [ ] Update REST DTOs to include domainData
- [ ] Run tests
- [ ] Commit: `refactor(ledger): update persistence-memory and rest for domainData`

### Task 1.5: Verify api/ tier purity

- [ ] Verify api/ pom.xml has no `jakarta.persistence-api` dependency
- [ ] Search for `jakarta.persistence` in api/src/ — expect zero results
- [ ] Run full platform build across downstream repos
- [ ] Commit if fixups needed: `refactor(ledger): api/ tier purity verification`

---

## Phase 2: Code Generator Extension

**Goal:** Build the `@PlatformService` annotation model and a Quarkus build-time extension that generates REST, GraphQL, MCP, and client code from annotated service interfaces.

**Repo:** casehub-platform (annotations + generator)
**Issues:** New issues needed — generator is the centrepiece that replaces hand-written Tasks 2.1–2.7 from the prior plan

**Independent of Phase 1** — generator works against existing service interfaces.

### Task 2.1: Annotation model in casehub-platform-api

**Repo:** casehub-platform
**Files:**
- Create: `api/src/main/java/io/casehub/platform/api/service/PlatformService.java`
- Create: `api/src/main/java/io/casehub/platform/api/service/Query.java`
- Create: `api/src/main/java/io/casehub/platform/api/service/Mutation.java`
- Create: `api/src/main/java/io/casehub/platform/api/service/Subscription.java`

**Interfaces:**
- Produces: `@PlatformService(domain = "engine", summary = "Case lifecycle and definitions")` — class-level, identifies a service interface for generation
- Produces: `@Query(description = "...")` — marks a read operation
- Produces: `@Mutation(description = "...")` — marks a write operation
- Produces: `@Subscription(description = "...")` — marks a real-time event stream
- All annotations are pure Java, retention RUNTIME, in `casehub-platform-api` (Tier 1)

**Design constraints:**
- Annotations carry operation metadata only — description, custom name override
- Parameter names, types, return types derived from method signatures (no duplication)
- `domain` attribute on `@PlatformService` is the only MCP hierarchy input
- Multiple interfaces with the same `domain` merge into one group

- [ ] Create annotation types
- [ ] Write tests verifying annotation retention and discoverability via reflection
- [ ] Commit: `feat(platform): @PlatformService annotation model for code generation`

### Task 2.2: Quarkus deployment module — REST generator

**Repo:** casehub-platform
**Files:**
- Create: `generator/pom.xml` — new submodule `casehub-platform-generator`
- Create: `generator-deployment/pom.xml` — Quarkus deployment module
- Create: `generator-deployment/src/main/java/io/casehub/platform/generator/PlatformServiceProcessor.java`
- Test: `generator-deployment/src/test/java/`

**Interfaces:**
- Consumes: `@PlatformService` annotated interfaces discovered via Jandex
- Produces: JAX-RS resource classes with `@Path`, `@GET`/`@POST`, `@RunOnVirtualThread`

**Pattern:** Quarkus `@BuildStep` processor that:
1. Scans for `@PlatformService` interfaces via Jandex
2. For each `@Query` method → generates `@GET` endpoint
3. For each `@Mutation` method → generates `@POST` endpoint
4. Generated resource class `@Inject`s the service interface and delegates
5. Path convention: `/api/v1/{domain}/{operation}` or derived from method name
6. DTO generation: request/response wrappers for complex parameters and return types
7. Error mapping: RFC 7807 ProblemDetail for exceptions

**Start with REST only** — validate the generator pattern works before adding GraphQL and MCP. The existing hand-written REST modules are the regression target.

- [ ] Create Quarkus deployment module skeleton
- [ ] Implement Jandex scanning for `@PlatformService` interfaces
- [ ] Implement REST resource class generation for `@Query` methods
- [ ] Implement REST resource class generation for `@Mutation` methods
- [ ] Implement DTO generation for complex parameters
- [ ] Write tests: annotated interface → generated REST resource → working endpoint
- [ ] Commit: `feat(platform): code generator — REST endpoint generation from @PlatformService`

### Task 2.3: GraphQL generator

**Repo:** casehub-platform
**Files:**
- Modify: `generator-deployment/` — add GraphQL generation to the processor

**Interfaces:**
- Consumes: same `@PlatformService` Jandex scan
- Produces: `@GraphQLApi` resolver classes with `@Query`/`@Mutation`/`@Subscription` (SmallRye)

**Changes:**
- Add SmallRye GraphQL dependency
- Custom scalars: UUID, Instant, JSON (shared, generated once)
- Shared pagination types: PageInput, PageInfo (generated once, reused)
- For each `@Query` → generate SmallRye `@Query` resolver method
- For each `@Mutation` → generate SmallRye `@Mutation` resolver method
- For each `@Subscription` → generate SmallRye subscription with `@Channel` wiring to CDI events
- GraphQL type names prefixed by domain to prevent collision across modules

- [ ] Add GraphQL generation to the build-step processor
- [ ] Implement custom scalar generation (UUID, Instant, JSON)
- [ ] Implement shared pagination type generation
- [ ] Implement subscription generation with `@Channel` wiring
- [ ] Write tests: annotated interface → generated GraphQL resolver → working query/mutation
- [ ] Commit: `feat(platform): code generator — GraphQL resolver generation`

### Task 2.4: MCP generator

**Repo:** casehub-platform
**Files:**
- Modify: `generator-deployment/` — add MCP generation to the processor
- Create: MCP tool implementations (fixed set: `casehub_model`, `casehub_action`)

**Interfaces:**
- Consumes: same `@PlatformService` Jandex scan
- Produces: `ModelProvider` classes that contribute domain subtrees to the model
- Produces: Action catalog entries that `casehub_action` dispatches to

**MCP tool set (fixed, never grows with operations):**
- `casehub_model(path?)` — returns hierarchical catalog. No path = domain list with summaries. Path = `{domain}` returns all operations with parameter details.
- `casehub_action(domain, operation, params)` — dispatches to the service method

**Generation:**
- For each `@PlatformService(domain = X)` → generate `ModelProvider` that lists operations under domain X
- Operation entries include: name, type (query/mutation), parameter names and types, return type, description
- Multiple interfaces with same domain merge at runtime
- Generation counter: monotonic counter incremented when model changes (service registration/deregistration)

- [ ] Create fixed MCP tools: `casehub_model`, `casehub_action`
- [ ] Implement ModelProvider generation from `@PlatformService` interfaces
- [ ] Implement action dispatch: `casehub_action` → resolve domain → resolve operation → invoke service method
- [ ] Write tests: annotated interface → model returns operations → action dispatches correctly
- [ ] Commit: `feat(platform): code generator — MCP model provider generation`

### Task 2.5: Typed client generation

**Repo:** casehub-platform
**Files:**
- Create: `graphql-client/pom.xml` — `casehub-graphql-client`

**Interfaces:**
- Consumes: generated GraphQL schema
- Produces: SmallRye GraphQL Client interfaces with typed methods

**Approach:** SmallRye GraphQL Client (MicroProfile) — annotated Java interfaces mirroring the generated schema. Code-first on both server and client.

- [ ] Set up SmallRye GraphQL Client module
- [ ] Generate or define typed client interfaces
- [ ] Create `CaseHubClient` factory with connection configuration
- [ ] Write tests against scaffold
- [ ] Commit: `feat(platform): typed CaseHub GraphQL client`

---

## Phase 3: Annotate Foundation Interfaces + Retire Hand-Written REST

**Goal:** Annotate all foundation service interfaces with `@PlatformService`. Validate generated REST matches existing hand-written REST. Retire hand-written REST modules. Wire everything into scaffold.

**Repos:** casehub-engine, casehub-work, casehub-ledger, casehub-qhorus, scaffold
**Issues:** casehubio/engine#892, casehubio/work#347, casehubio/ledger#190, casehubio/qhorus#394, casehubio/parent#406

**Prerequisite:** Phase 2 (generator works)

### Task 3.1: Annotate casehub-engine interfaces

**Repo:** casehub-engine
**Files:**
- Modify: `api/src/main/java/io/casehub/api/engine/CaseHubRuntime.java` — add `@PlatformService(domain = "engine")`
- Modify: `common/src/main/java/.../CaseDefinitionRegistry.java` — add annotations
- Modify: `common/src/main/java/.../CaseInstanceRepository.java` — annotate query methods
- Test: verify generated REST matches existing `casehub-engine-rest` endpoints

**Interfaces:**
- Produces: `CaseHubRuntime` annotated with `@PlatformService(domain = "engine")`, methods annotated with `@Query`/`@Mutation`

**Validation:** Compare generated REST endpoints against hand-written `casehub-engine-rest` — same paths, same HTTP methods, same request/response shapes. Differences are either bugs in the generator or improvements to document.

- [ ] Annotate `CaseHubRuntime` with `@PlatformService` and operation annotations
- [ ] Annotate `CaseDefinitionRegistry` query methods
- [ ] Build and verify generated REST matches engine-rest
- [ ] Document any intentional differences
- [ ] Commit: `feat(engine): annotate service interfaces with @PlatformService`

### Task 3.2: Annotate casehub-work interfaces

**Repo:** casehub-work
**Files:**
- Extract: `WorkItemService` interface to api/ (currently concrete class in runtime/)
- Extract: `WorkItemLifecycleEvent` to api/ (currently in runtime/)
- Modify: new `WorkItemService` interface — add `@PlatformService(domain = "work")`

**Prerequisite within task:** Extract the interface FIRST, then annotate it. The graphql and MCP generators need the interface in api/ to avoid pulling in JPA via runtime/.

- [ ] Extract `WorkItemService` interface to api/
- [ ] Extract `WorkItemLifecycleEvent` to api/
- [ ] Annotate `WorkItemService` with `@PlatformService(domain = "work")`
- [ ] Build and verify generated REST matches work-rest
- [ ] Commit: `feat(work): extract WorkItemService to api/, annotate with @PlatformService`

### Task 3.3: Annotate casehub-ledger interfaces

**Repo:** casehub-ledger
**Files:**
- Modify: `api/src/main/java/io/casehub/ledger/api/spi/TrustScoreSource.java` — annotate
- Modify: `api/src/main/java/io/casehub/ledger/api/spi/LedgerEntryRepository.java` — annotate query methods
- Modify: `api/src/main/java/io/casehub/ledger/api/spi/LedgerAppender.java` — annotate

**Additional:** Add composite `trustRoutingProfile` method to `TrustScoreSource` — returns global score + capability score + decision count + quality dimensions in one call. Annotated as `@Query`. This replaces the chatty individual-call pattern.

- [ ] Add `trustRoutingProfile` composite method to `TrustScoreSource`
- [ ] Annotate `TrustScoreSource`, `LedgerEntryRepository` query methods, `LedgerAppender`
- [ ] Build and verify generated REST matches ledger-rest
- [ ] Commit: `feat(ledger): annotate service interfaces with @PlatformService`

### Task 3.4: Annotate casehub-qhorus interfaces

**Repo:** casehub-qhorus
**Files:**
- Modify: `api/src/main/java/io/casehub/qhorus/api/channel/ChannelManager.java` — annotate
- Modify: `api/src/main/java/io/casehub/qhorus/api/message/MessageDispatcher.java` — annotate
- Modify: `api/src/main/java/io/casehub/qhorus/api/channel/ChannelReader.java` — annotate

- [ ] Annotate `ChannelManager`, `MessageDispatcher`, `ChannelReader`
- [ ] Build and verify generated REST matches qhorus REST endpoints
- [ ] Commit: `feat(qhorus): annotate service interfaces with @PlatformService`

### Task 3.5: Scaffold as unified CaseHub server

**Repo:** scaffold
**Files:**
- Modify: `pom.xml` — add dependency on generator runtime + all foundation api modules
- Modify: `src/main/resources/application.properties` — GraphQL + MCP configuration

**Changes:**
- Generator auto-discovers all `@PlatformService` interfaces from classpath
- REST, GraphQL, and MCP all compose automatically — no per-module wiring
- Verify composed GraphQL schema via introspection (no type name collisions)
- Verify MCP model returns all domains
- Configure GraphQL UI for dev mode
- Integration tests: full lifecycle via each presentation layer

- [ ] Add generator + foundation api dependencies
- [ ] Configure GraphQL and MCP endpoints
- [ ] Integration test: startCase via REST, query via GraphQL, discover via MCP
- [ ] Integration test: cross-module flow (start case → create work item → trust query)
- [ ] Verify GraphQL UI shows composed schema
- [ ] Verify `casehub_model()` returns all domains
- [ ] Commit: `feat(scaffold): unified CaseHub server — generated REST + GraphQL + MCP`

### Task 3.6: Retire hand-written REST modules

**Repos:** casehub-engine, casehub-work, casehub-ledger, casehub-qhorus

**Prerequisite:** Generated REST validated against hand-written REST in Tasks 3.1–3.4.

- [ ] Remove `casehub-engine-rest` module (or mark deprecated)
- [ ] Remove `casehub-work-rest` module
- [ ] Remove `casehub-ledger-rest` module
- [ ] Remove qhorus hand-written REST endpoints (in runtime/)
- [ ] Update all downstream consumers that depended on these modules
- [ ] Run full platform build
- [ ] Commit per repo: `refactor(<repo>): retire hand-written REST — replaced by generated`

---

## Phase 4: SPI Callback Mechanism

**Goal:** Enable remote apps to implement platform SPIs via webhook callbacks. When an app connects to a CaseHub server, it can register callbacks for SPIs it implements. The server calls the app's endpoint when the SPI is invoked.

**Repos:** casehub-platform, casehub-engine, casehub-work
**Issues:** casehubio/platform#230, casehubio/engine#893, casehubio/work#348, casehubio/platform#231

**Prerequisite:** Phase 3 complete (presentation layers working)

### Task 4.1: Callback registration API

**Repo:** casehub-platform
**Files:**
- Create: `callback-api/pom.xml` — `casehub-platform-callback-api`
- Create: `CallbackRegistration.java`, `CallbackRegistry.java`, `CallbackInvoker.java`

**Interfaces:**
- `CallbackRegistration` record — `(spiName, callbackUrl, credentialRef, timeout, metadata)`
- `CallbackRegistry` SPI — register (idempotent upsert), unregister, findCallbacks. TTL-based lease with heartbeat renewal.
- `CallbackInvoker` — HTTP POST with retry (3x exponential backoff), circuit breaker (5 consecutive failures → open)

Registration exposed via the generated presentation layers (annotate `CallbackRegistry` with `@PlatformService(domain = "platform")`).

- [ ] Define callback model and registry SPI
- [ ] Implement in-memory and JPA callback registries
- [ ] Implement CallbackInvoker
- [ ] Annotate with `@PlatformService` (auto-generates REST + GraphQL + MCP)
- [ ] Write tests
- [ ] Commit: `feat(platform): callback registration API and registry`

### Task 4.2: WorkerProvisioner callback adapter

**Repo:** casehub-engine
- `CallbackWorkerProvisioner` — `@ApplicationScoped`, displaces `@DefaultBean` NoOp
- Idempotency key per provision request
- [ ] Implement, test with WireMock
- [ ] Commit: `feat(engine): WorkerProvisioner callback adapter`

### Task 4.3: ActionRiskClassifier callback adapter

**Repo:** casehub-engine
- `CallbackActionRiskClassifier` — `@RiskClassifier @ApplicationScoped`, joins ChainedActionRiskClassifier chain
- Fans out to all registered remote classifiers in parallel, most-restrictive-wins
- [ ] Implement, test with WireMock
- [ ] Commit: `feat(engine): ActionRiskClassifier callback adapter`

### Task 4.4: SlaBreachPolicy callback adapter

**Repo:** casehub-work
- Prereq: add `@DefaultBean` to `NoOpSlaBreachPolicy`
- `CallbackSlaBreachPolicy` — `@ApplicationScoped`, displaces `@DefaultBean`
- [ ] Fix NoOpSlaBreachPolicy annotation, implement adapter, test
- [ ] Commit: `feat(work): SlaBreachPolicy callback adapter`

### Task 4.5: WorkerSelectionStrategy callback adapter

**Repo:** casehub-work
- `CallbackWorkerSelectionStrategy` — `@Unremovable @ApplicationScoped` with `id() = "callback"`
- Named multi-bean pattern — joins existing strategy collection
- [ ] Implement, test with WireMock
- [ ] Commit: `feat(work): WorkerSelectionStrategy callback adapter`

### Task 4.6: Client-side auto-registration

**Repo:** casehub-platform
- `CallbackAutoRegistrar` — `@Startup` CDI bean, discovers local SPI implementations
- Static JAX-RS dispatch endpoint: `POST /casehub/callbacks/{spiName}`
- Startup resilience: retry with exponential backoff, app starts regardless
- Lease renewal: re-registers on TTL/2 timer
- Health check: `/health/ready` DOWN while registrations pending

- [ ] Implement auto-discovery and dispatch endpoint
- [ ] Implement auto-registration with retry
- [ ] Write integration tests
- [ ] Commit: `feat(platform): client-side SPI callback auto-registration`

---

## Cross-Phase: Documentation

**Phase 1 complete:**
- Update `docs/platform/persistence.md` — orm.xml mapping, domainData
- Update ledger consumer/contributor guides
- ADR for orm.xml approach

**Phase 2 complete:**
- Create `docs/platform/code-generator.md` — annotation model, how to add operations
- Create protocol: `@PlatformService` conventions
- ADR for unified generation approach

**Phase 3 complete:**
- Update `docs/INDEX.md` — GraphQL + MCP references
- Update `docs/guides/building-apps.md` — GraphQL as recommended starting point, MCP for agents
- Update `docs/platform/overview.md` — scaffold as CaseHub server
- Update scaffold CLAUDE.md

**Phase 4 complete:**
- Create `docs/platform/callbacks.md` — SPI callback conventions
- Update `docs/platform/boundary-rules.md` — callback-eligible SPIs
- Update `docs/guides/building-apps.md` — callback mechanism

---

## Execution Order Summary

```
Phase 1 (Ledger — independent)    Phase 2 (Generator — independent)

T1.1  Strip JPA from api/         T2.1  Annotation model in platform-api
  ↓                                 ↓
T1.2  orm.xml in runtime/         T2.2  REST generator (Quarkus build-step)
  ↓                                 ↓
T1.2a Verify cross-JAR (GATE)     T2.3  GraphQL generator
  ↓                                 ↓
T1.3  domainData field            T2.4  MCP generator
  ↓                                 ↓
T1.4  Memory + REST               T2.5  Typed client
  ↓
T1.5  Verify tier purity


Phase 3 (Annotate + Wire — depends on Phase 2)

T3.1  Annotate engine interfaces
T3.2  Annotate work interfaces (extract WorkItemService to api/ first)
T3.3  Annotate ledger interfaces (add trustRoutingProfile)
T3.4  Annotate qhorus interfaces
  ↓
T3.5  Scaffold as unified server
  ↓
T3.6  Retire hand-written REST modules


Phase 4 (Callbacks — depends on Phase 3)

T4.1  Callback registration API
  ↓
T4.2  WorkerProvisioner adapter
T4.3  ActionRiskClassifier adapter
T4.4  SlaBreachPolicy adapter
T4.5  WorkerSelectionStrategy adapter
  ↓
T4.6  Client-side auto-registration
```

**Phase independence:** Phases 1 and 2 can proceed in parallel. Phase 3 depends on Phase 2. Phase 4 depends on Phase 3. Within Phase 3, Tasks 3.1–3.4 can be parallelised. Within Phase 4, Tasks 4.2–4.5 can be parallelised.
