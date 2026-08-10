# Dual-Mode Platform Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** casehubio/parent#405 — Dual-Mode Platform: Embedded + Centralized (GraphQL)
**Issue group:** Spans casehub-ledger, casehub-engine, casehub-work, casehub-qhorus, casehub-platform, scaffold

**Goal:** Enable the CaseHub platform to operate in two deployment models — embedded (library) and centralized (CaseHub server via scaffold + GraphQL) — without changing embedded app code.

**Architecture:** Three layered phases. Phase 1 fixes the ledger tier violation — stripping JPA annotations from casehub-ledger-api and moving mappings to runtime/ via orm.xml — while adding a `domainData` extension field for remote entries. Phase 2 adds GraphQL as the remote API with per-module schemas, custom scalars, and CDI-to-subscription wiring. Phase 3 adds SPI callback registration so remote apps can extend platform behavior via webhooks. Phases 1 and 2 are independent and can proceed in parallel; Phase 3 depends on Phase 2.

**Tech Stack:** Quarkus 3.32+, SmallRye GraphQL (MicroProfile GraphQL), PostgreSQL JSONB, Jackson, virtual threads (Java 21)

## Global Constraints

- Pre-release platform: breaking changes cost nothing. Fix the design.
- Module tier structure: api/ is pure Java (no JPA, no Quarkus runtime). JPA lives in runtime/ or persistence-* modules only.
- Virtual threads: no `Uni<T>` in SPIs. Blocking interfaces only (ADR-0005).
- Flyway conventions: per-module migration ranges per `docs/platform/persistence.md`.
- All commits reference an issue.
- IntelliJ MCP mandatory for .java file operations.

---

## Phase 1: Ledger Tier Violation Fix

**Goal:** Strip JPA annotations from `casehub-ledger-api` to restore tier purity. Move JPA field mappings to `runtime/` via `orm.xml`. Add `domainData` extension field for remote entry support in centralized mode. Preserve JOINED inheritance, typed domain subtypes, and the `domainContentBytes()` build-time enforcement mechanism.

**Rationale:** The ledger's JOINED inheritance model was a deliberate architectural choice (ARC42STORIES.MD §4 Solution Strategy): "Consumers add domain fields via subclass join tables without modifying the base schema. Single Table rejected — column explosion across consumers. Table Per Class rejected — no polymorphic queries." The typed subtypes (`ActorIdentityBindingEntry`, `KeyRotationEntry`, `CaseLedgerEntry`, `WorkItemLedgerEntry`, `WorkerDecisionEntry`, `MessageLedgerEntry`, `ErasureReceiptLedgerEntry`) carry compile-time guarantees enforced by `LedgerProcessor` — subclasses with persistent fields that don't override `domainContentBytes()` produce a build-time error. This architecture is sound and serves the platform well. The tier violation (JPA annotations in api/) is a code hygiene issue worth fixing — it does not require abandoning the inheritance model.

**Repos:** casehub-ledger

### Task 1.1: Strip JPA annotations from LedgerEntry and LedgerSupplement in api/

**Repo:** casehub-ledger
**Files:**
- Modify: `api/src/main/java/io/casehub/ledger/api/model/LedgerEntry.java`
- Modify: `api/src/main/java/io/casehub/ledger/api/model/supplement/LedgerSupplement.java`
- Test: `api/src/test/java/io/casehub/ledger/api/model/LedgerEntryTest.java`

**Interfaces:**
- Produces: `LedgerEntry` as a plain abstract Java class — no `@MappedSuperclass`, no `@Column`, no `@Id`, no `@Enumerated`, no `@Transient`. All fields remain as public fields with identical names and types.
- Produces: `LedgerSupplement` as a plain abstract Java class — no `@MappedSuperclass`.
- Unchanged: `canonicalBytes()`, `domainContentBytes()`, `attach()`, all supplement helpers, `AuditRecord`, `OutcomeRecord`.

**Changes:**
- Remove all `jakarta.persistence` imports and annotations from `LedgerEntry`
- Remove `@MappedSuperclass` from `LedgerSupplement`
- Fields retain identical names, types, and default values — only annotations are stripped
- No domain model changes — `AuditRecord`, `OutcomeRecord`, factory methods all unchanged

**LedgerProcessor validation unaffected:** `LedgerProcessor.validateDomainContentBytes()` checks `@Entity` annotations on LedgerEntry *subclasses* (in runtime/ and consumer modules), not on `LedgerEntry` itself. Stripping annotations from `LedgerEntry` in api/ does not interact with this validation — the build-time enforcement mechanism survives Phase 1 unchanged.

- [ ] Strip JPA annotations from LedgerEntry (remove @MappedSuperclass, @Id, @Column, @Enumerated, @Transient)
- [ ] Strip @MappedSuperclass from LedgerSupplement
- [ ] Verify api/ module compiles with zero `jakarta.persistence` imports
- [ ] Run `mvn --batch-mode install` on api/ module
- [ ] Commit: `refactor(ledger): strip JPA annotations from api/ — fix tier violation`

### Task 1.2: Define JPA mappings via orm.xml in runtime/

**Repo:** casehub-ledger
**Files:**
- Create: `runtime/src/main/resources/META-INF/orm.xml`
- Test: existing runtime tests, consumer-compat-test

**Interfaces:**
- Consumes: `LedgerEntry` (plain Java from Task 1.1)
- Produces: orm.xml that declares `LedgerEntry` as `<mapped-superclass>` with column mappings for all fields
- Produces: orm.xml that declares `LedgerSupplement` as `<mapped-superclass>`
- Unchanged: `JpaLedgerEntry` (`@Entity`, `@Inheritance(JOINED)`), all entity subtypes, all `@NamedQuery`

**Changes:**
- Create `runtime/src/main/resources/META-INF/orm.xml` with `<mapped-superclass>` declarations
- Map all LedgerEntry fields: `id` (PK), `subjectId`, `tenancyId`, `sequenceNumber`, `entryType` (EnumType.STRING), `actorId`, `actorType` (EnumType.STRING), `actorRole`, `occurredAt`, `digest`, `traceId`, `causedByEntryId`, `agentSignature`, `agentPublicKey`, `agentKeyRef`, `actorDid`, `metadata`, `supplementJson`
- Mark `supplements` list as `<transient/>` (previously `@Transient`)
- Map LedgerSupplement fields similarly
- `JpaLedgerEntry` and all entity subtypes (`ActorIdentityBindingEntry`, `KeyRotationEntry`, `CaseLedgerEntry`, etc.) continue to work unchanged — they extend a mapped superclass now mapped via XML instead of annotations
- Supplement relationships (`complianceSupplements`, `provenanceSupplements` `@OneToMany`) remain on `JpaLedgerEntry` unchanged

- [ ] Create orm.xml with LedgerEntry mapped-superclass declaration
- [ ] Create orm.xml with LedgerSupplement mapped-superclass declaration
- [ ] Run full runtime test suite — verify all queries, persistence, and hash chain work
- [ ] Run consumer-compat-test module
- [ ] Run `mvn --batch-mode install` on runtime/ module
- [ ] Commit: `refactor(ledger): orm.xml owns JPA mappings for LedgerEntry — api/ tier-pure`

### Task 1.3: Add domainData extension field

**Repo:** casehub-ledger
**Files:**
- Modify: `api/src/main/java/io/casehub/ledger/api/model/LedgerEntry.java`
- Modify: orm.xml (from Task 1.2) — add `domain_data` column mapping
- Create: Flyway migration for `domain_data JSONB` column
- Create: `runtime/.../model/DomainDataConverter.java` — JPA `AttributeConverter<Map<String, Object>, String>`
- Test: new tests for domainData serialization and canonicalBytes integration

**Interfaces:**
- Produces: `public Map<String, Object> domainData` field on `LedgerEntry` (default: null)
- Produces: `domain_data JSONB` column on `ledger_entry` table
- Unchanged: `domainContentBytes()` — typed subtypes continue to use it for hash-protected domain fields

**Changes:**
- Add `public Map<String, Object> domainData` field to `LedgerEntry` (plain field, no annotations)
- Map field in orm.xml with `@Convert` to `DomainDataConverter` (Map ↔ JSONB string)
- Add Flyway migration: `ALTER TABLE ledger_entry ADD COLUMN domain_data JSONB`
- Update `canonicalBytes()` to include `domainData` when non-null and non-empty — serialized via RFC 8785 (JSON Canonicalization Scheme — JCS) using `org.erdtman:java-json-canonicalization`: recursive lexicographic key sorting, IEEE 754 number serialization, null-valued keys omitted (absent ≡ null), UTF-8 encoding. **Java type mapping rules for `Map<String, Object>` deserialization:** all JSON integers deserialize as `Long`, all JSON decimals as `BigDecimal`, all JSON strings as `String`, all JSON booleans as `Boolean`, nested objects as `Map<String, Object>`, arrays as `List<Object>`. `DomainDataConverter` enforces these types on read — Jackson's `DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS` and `USE_LONG_FOR_INTS` flags guarantee deterministic round-trip. This is ADDITIVE: existing entries have null `domainData` and produce identical hashes. `domainData` and `domainContentBytes()` are complementary — typed subtypes use `domainContentBytes()` for their typed fields, `PlainLedgerEntry` instances from remote apps use `domainData`. Deterministic `canonicalBytes()` is required for Merkle chain integrity — JCS guarantees that the same `domainData` always produces identical bytes regardless of insertion order or JSON parser implementation.
- Add GIN index on `domain_data` for JSONB containment queries

- [ ] Add domainData field to LedgerEntry
- [ ] Create DomainDataConverter (Map ↔ JSONB)
- [ ] Add orm.xml mapping for domainData with converter
- [ ] Write Flyway migration for domain_data column + GIN index
- [ ] Write tests: canonicalBytes with null domainData produces same hash as before
- [ ] Write tests: canonicalBytes with populated domainData includes it deterministically
- [ ] Write tests: domainData round-trip through JPA
- [ ] Run full test suite
- [ ] Commit: `feat(ledger): domainData extension field for remote entry support`

### Task 1.4: Update persistence-memory and ledger-rest

**Repo:** casehub-ledger
**Files:**
- Modify: `persistence-memory/src/main/java/io/casehub/ledger/memory/InMemoryLedgerEntryRepository.java`
- Modify: `rest/src/main/java/io/casehub/ledger/rest/` (DTOs)
- Test: persistence-memory tests, rest tests

**Changes:**
- In-memory repo: Map field is naturally in-memory — minimal or no changes
- REST resources: expose `domainData` in response DTOs, accept in POST body
- Update DTOs to include `domainData: Map<String, Object>`

- [ ] Update InMemoryLedgerEntryRepository if needed for domainData
- [ ] Update REST DTOs to include domainData
- [ ] Run tests
- [ ] Commit: `refactor(ledger): update persistence-memory and rest for domainData field`

### Task 1.5: Verify api/ tier purity

**Repo:** casehub-ledger

- [ ] Verify api/ pom.xml has no `jakarta.persistence-api` dependency
- [ ] Run `ide_search_text` for `jakarta.persistence` in api/src/ — expect zero results
- [ ] Run full platform build across all foundation repos
- [ ] Commit (if any fixups needed): `refactor(ledger): api/ tier purity verification`

---

## Phase 2: GraphQL Platform

**Goal:** Add GraphQL as the remote API for CaseHub. Per-module schemas in foundation repos. Custom scalars and shared pagination types. CDI events wired to GraphQL subscriptions via `@Channel`. Scaffold as the unified CaseHub server. Typed Java client generation.

**Repos:** casehub-engine, casehub-work, casehub-ledger, casehub-qhorus, casehub-platform (shared GraphQL types), scaffold

**No prerequisite on Phase 1** — GraphQL resolvers delegate to existing service interfaces (`CaseHubRuntime`, `WorkItemService`, `LedgerEntryRepository`, etc.) which exist now. Typed domain models (e.g., `CaseLedgerEntry` with typed `caseId`, `commandType` fields) map to GraphQL types more naturally than opaque JSON blobs.

**Resolver execution model:** All GraphQL resolvers use blocking interfaces (virtual threads per ADR-0005). Scaffold includes `quarkus-hibernate-reactive-panache` for its own persistence, but platform SPIs are blocking — the two stacks coexist without conflict. SmallRye GraphQL on Quarkus supports both blocking and non-blocking resolvers; this spec uses blocking exclusively.

### Task 2.1: GraphQL foundation module in casehub-platform

**Repo:** casehub-platform
**Files:**
- Create: `graphql/pom.xml` — new submodule `casehub-platform-graphql`
- Create: `graphql/src/main/java/io/casehub/platform/graphql/` package
- Create: `graphql/src/main/java/io/casehub/platform/graphql/scalar/` — custom scalars (UUID, Instant, JSON)
- Test: `graphql/src/test/java/io/casehub/platform/graphql/scalar/`

**Interfaces:**
- Produces: Custom scalars: `UUIDScalar`, `InstantScalar`, `JsonScalar` (for `Map<String, Object>` payloads)
- Produces: Shared pagination types: `PageInput` (offset, limit, cursor), `PageInfo` (hasNext, hasPrevious, totalCount, cursor). All domain modules use these shared types to prevent type name collisions across the unified schema.
- Produces: Shared GraphQL error types (RFC 7807 `ProblemDetail` → GraphQL error format)

**Key dependency:** `quarkus-smallrye-graphql` (SmallRye GraphQL — MicroProfile GraphQL implementation)

**Changes:**
- Add module to platform root pom.xml
- Implement custom scalars for UUID, Instant, and JSON Map types
- Define shared pagination types used by all domain modules
- Define shared error response format

**Note on cross-mutation composition:** The `@export` directive for server-side inter-mutation variable passing was removed. SmallRye GraphQL does not support custom directives that modify input argument resolution — implementing this would require forking the resolver chain or building a fragile pre-processing layer. The standard alternative is simpler and well-supported: the client calls mutation A, extracts the returned ID, and passes it to mutation B in a second request. This is how production GraphQL APIs handle cross-entity creation.

- [ ] Create module skeleton with SmallRye GraphQL dependency
- [ ] Implement custom scalars (UUID, Instant, JSON Map)
- [ ] Define shared pagination types (`PageInput`, `PageInfo`) in platform-graphql
- [ ] Define shared error types
- [ ] Write tests for custom scalars
- [ ] Verify tests pass
- [ ] Commit: `feat(platform): GraphQL foundation — custom scalars, shared pagination types`

### Task 2.2: Engine GraphQL schema

**Repo:** casehub-engine
**Files:**
- Create: `graphql/pom.xml` — new submodule `casehub-engine-graphql`
- Create: `graphql/src/main/java/io/casehub/engine/graphql/CaseQueryResolver.java`
- Create: `graphql/src/main/java/io/casehub/engine/graphql/CaseMutationResolver.java`
- Create: `graphql/src/main/java/io/casehub/engine/graphql/CaseSubscriptionResolver.java`
- Create: `graphql/src/main/java/io/casehub/engine/graphql/dto/` — GraphQL DTOs
- Test: `graphql/src/test/java/io/casehub/engine/graphql/`

**Interfaces:**
- Consumes: `CaseHubRuntime` (api/), `CaseService`, `CaseDefinitionRegistry`, `CaseInstanceRepository`, `EventLogRepository`
- Produces: GraphQL schema:

```graphql
type Query {
  cases(filter: CaseFilterInput, page: PageInput): CasePage!
  caseById(caseId: ID!): CaseInstance
  caseContext(caseId: ID!, path: String): JSON
  caseDefinitions(page: PageInput): CaseDefinitionPage!
  caseDefinition(namespace: String!, name: String!, version: String): CaseDefinitionResponse
  casePlanItems(caseId: ID!): [PlanItem!]!
  caseGoals(caseId: ID!): [GoalStatus!]!
  caseEvents(caseId: ID!, eventTypes: [String!], page: PageInput): EventLogPage!
}

type Mutation {
  startCase(input: StartCaseInput!): CaseInstance!
  signalCase(caseId: ID!, path: String!, value: JSON): SignalResult!
  suspendCase(caseId: ID!): CaseControl!
  resumeCase(caseId: ID!): CaseControl!
  cancelCase(caseId: ID!): CaseControl!
}

type Subscription {
  caseLifecycle(caseId: ID!): CaseLifecycleEvent!
  caseContextChange(caseId: ID!): CaseContextChangeEvent!
}
```

**Pattern:** Resolvers are `@ApplicationScoped` beans that `@Inject` the service interfaces and delegate. Same thin-adapter pattern as REST resources. ACL checks via `CaseService.requireCaseAccess()`. Tenancy via `CurrentPrincipal.tenancyId()`.

**Subscriptions:** Wire CDI `CaseLifecycleEvent` → SmallRye GraphQL `Multi<CaseLifecycleEvent>` publisher. Platform standard: use `@Channel` from SmallRye Reactive Messaging (not `BroadcastProcessor`). `@Channel` provides consistent infrastructure-backed pub/sub across all modules — starts with in-memory connector, scales to Kafka/AMQP via configuration. Apply `onOverflow().buffer(256)` with drop-oldest strategy to prevent OOM under slow consumers. Subscriptions are best-effort live notifications — the ledger is the authoritative event source for replay. Clients that reconnect after a gap should query the ledger for missed events.

**Channel naming convention:** All SmallRye Reactive Messaging channel names use the format `{module}-{event-type}` to prevent name collisions when modules are composed in scaffold. Existing: `qhorus-messages`. New channels: `engine-case-lifecycle`, `engine-case-context-change`, `work-item-lifecycle`, `work-item-inbox`. CDI event types provide type-safe routing; unique channel names prevent cross-contamination in the `mp.messaging` configuration namespace.

- [ ] Create module skeleton with dependencies on engine-api, engine-common, platform-graphql
- [ ] Implement query resolvers — delegate to CaseHubRuntime / repositories
- [ ] Write tests for queries (mock services, verify resolution)
- [ ] Implement mutation resolvers — delegate to CaseHubRuntime
- [ ] Write tests for mutations
- [ ] Implement subscription resolver — wire CDI events to Multi publisher
- [ ] Write subscription tests
- [ ] Commit: `feat(engine): GraphQL schema — queries, mutations, subscriptions`

### Task 2.3: Work GraphQL schema

**Repo:** casehub-work
**Files:**
- Create: `graphql/pom.xml` — new submodule `casehub-work-graphql`
- Create: `graphql/src/main/java/io/casehub/work/graphql/WorkItemQueryResolver.java`
- Create: `graphql/src/main/java/io/casehub/work/graphql/WorkItemMutationResolver.java`
- Create: `graphql/src/main/java/io/casehub/work/graphql/WorkItemSubscriptionResolver.java`
- Create: `graphql/src/main/java/io/casehub/work/graphql/dto/` — GraphQL DTOs
- Test: `graphql/src/test/java/io/casehub/work/graphql/`

**Interfaces:**
- Consumes: `WorkItemService` (api/ — extracted per Prerequisite below), `WorkItemStore` (api/)
- Produces: GraphQL schema covering:

```graphql
type Query {
  workItems(filter: WorkItemFilterInput, page: PageInput): WorkItemPage!
  workItemById(id: ID!): WorkItem
  workItemInbox(assignee: String, candidateGroups: [String!], page: PageInput): WorkItemPage!
  workItemInboxSummary(assignee: String): InboxSummary!
}

type Mutation {
  createWorkItem(input: CreateWorkItemInput!): WorkItem!
  claimWorkItem(id: ID!, claimant: String!): WorkItem!
  startWorkItem(id: ID!): WorkItem!
  completeWorkItem(id: ID!, resolution: JSON, outcome: String): WorkItem!
  rejectWorkItem(id: ID!, reason: String): WorkItem!
  delegateWorkItem(id: ID!, targetActor: String!): WorkItem!
  suspendWorkItem(id: ID!): WorkItem!
  resumeWorkItem(id: ID!): WorkItem!
  cancelWorkItem(id: ID!, reason: String): WorkItem!
  escalateWorkItem(id: ID!, targetGroups: [String!]!): WorkItem!
}

type Subscription {
  workItemLifecycle(id: ID): WorkItemLifecycleEvent!
  workItemInboxUpdates(assignee: String!): WorkItemLifecycleEvent!
}
```

**Prerequisite:** Extract both to api/ before implementing the GraphQL resolver:
1. `WorkItemService` — currently `io.casehub.work.runtime.service.WorkItemService` (concrete class). Extract interface to api/, runtime class becomes implementation.
2. `WorkItemLifecycleEvent` — currently `io.casehub.work.runtime.event.WorkItemLifecycleEvent`. Move to `io.casehub.work.api` so the GraphQL subscription resolver can observe it without depending on runtime/.
The graphql/ module depends on api/ interfaces only — this follows the established pattern (e.g., `CaseHubRuntime` interface in engine api/).

- [ ] Create module skeleton
- [ ] Implement query resolvers
- [ ] Implement mutation resolvers (lifecycle transitions)
- [ ] Implement subscription resolvers (CDI WorkItemLifecycleEvent → Multi)
- [ ] Write tests
- [ ] Commit: `feat(work): GraphQL schema — queries, mutations, subscriptions`

### Task 2.4: Ledger GraphQL schema

**Repo:** casehub-ledger
**Files:**
- Create: `graphql/pom.xml` — new submodule `casehub-ledger-graphql`
- Create: `graphql/src/main/java/io/casehub/ledger/graphql/` — resolvers + DTOs
- Test: `graphql/src/test/java/io/casehub/ledger/graphql/`

**Interfaces:**
- Consumes: `LedgerEntryRepository` (api/), `TrustScoreSource` (api/), `LedgerAppender` (api/)
- Produces: GraphQL schema:

```graphql
type Query {
  ledgerEntries(subjectId: ID, actorId: String, from: Instant, to: Instant, 
                entryType: String, page: PageInput): LedgerEntryPage!
  ledgerEntry(id: ID!): LedgerEntry
  trustScore(actorId: String!): TrustProfile!
  trustCapabilityScore(actorId: String!, capability: String!): CapabilityScore!
  # Batch query — solves the chatty routing problem
  trustRoutingProfile(actorId: String!, capability: String!): RoutingProfile!
  merkleVerification(subjectId: ID!): VerificationResult!
}

type Mutation {
  appendLedgerEntry(input: AppendEntryInput!): LedgerEntry!
  createAttestation(entryId: ID!, input: AttestationInput!): Attestation!
}
```

**Key:** `trustRoutingProfile` is a composite query — returns global score + capability score + decision count + quality dimensions in one call. This replaces the chatty pattern of calling `globalScore()` + `capabilityScore()` + `decisionCount()` + `qualityScores()` individually.

- [ ] Create module skeleton
- [ ] Implement query resolvers (including composite `trustRoutingProfile`)
- [ ] Implement mutation resolvers (`appendLedgerEntry` with domainData payload)
- [ ] Write tests
- [ ] Commit: `feat(ledger): GraphQL schema — queries, mutations, composite trust profile`

### Task 2.5: Qhorus GraphQL schema

**Repo:** casehub-qhorus
**Files:**
- Create: `graphql/pom.xml` — new submodule `casehub-qhorus-graphql`
- Create: `graphql/src/main/java/io/casehub/qhorus/graphql/` — resolvers + DTOs
- Test: `graphql/src/test/java/io/casehub/qhorus/graphql/`

**Interfaces:**
- Consumes: `ChannelManager` (api/), `MessageDispatcher` (api/), `ChannelReader` (api/)
- Produces: GraphQL schema covering channels, messages, commitments, subscriptions for message activity

```graphql
type Query {
  channels(filter: ChannelFilterInput, page: PageInput): ChannelPage!
  channel(id: ID!): Channel
  channelMessages(channelId: ID!, page: PageInput): MessagePage!
  commitments(channelId: ID, state: String, page: PageInput): CommitmentPage!
}

type Mutation {
  createChannel(input: CreateChannelInput!): Channel!
  deleteChannel(id: ID!, force: Boolean): Boolean!
  pauseChannel(id: ID!): Channel!
  resumeChannel(id: ID!): Channel!
  dispatchMessage(input: DispatchMessageInput!): DispatchResult!
}

type Subscription {
  channelActivity(channelId: ID!): MessageEvent!
  channelPresence(channelId: ID!): PresenceEvent!
}
```

- [ ] Create module skeleton
- [ ] Implement query and mutation resolvers
- [ ] Implement subscription resolvers
- [ ] Write tests
- [ ] Commit: `feat(qhorus): GraphQL schema — channels, messages, subscriptions`

### Task 2.6: Scaffold as unified CaseHub server

**Repo:** scaffold (mdproctor/flow)
**Files:**
- Modify: `pom.xml` — add dependencies on all graphql modules
- Modify: `src/main/resources/application.properties` — GraphQL configuration
- Create: `src/main/java/io/casehub/scaffold/graphql/ScaffoldGraphQLConfig.java` (if needed)
- Test: integration tests

**Interfaces:**
- Consumes: All graphql modules from Tasks 2.1–2.5
- Produces: Unified GraphQL endpoint at `/graphql` composing all module schemas

**Changes:**
- Add Maven dependencies: `casehub-platform-graphql`, `casehub-engine-graphql`, `casehub-work-graphql`, `casehub-ledger-graphql`, `casehub-qhorus-graphql`
- SmallRye GraphQL automatically discovers `@GraphQLApi` beans from all modules on classpath
- **Type collision prevention:** All modules use shared `PageInput`/`PageInfo` from `casehub-platform-graphql` (Task 2.1). Domain-specific types use module-prefixed names where collision is possible (e.g., `CaseFilterInput` vs `WorkItemFilterInput`, not bare `FilterInput`). SmallRye rejects duplicate type names at startup — verify via schema introspection.
- Configure `quarkus.smallrye-graphql.ui.always-include=true` for GraphQL UI in dev mode
- Verify the composed schema via introspection
- Write integration tests: start case via GraphQL, query it, verify lifecycle subscription

- [ ] Add graphql module dependencies to scaffold pom.xml
- [ ] Configure GraphQL endpoint in application.properties
- [ ] Write integration test: startCase mutation → caseById query → verify
- [ ] Write integration test: startCase → use returned caseId in createWorkItem (multi-request composition)
- [ ] Write integration test: subscription — start case, receive lifecycle event
- [ ] Verify GraphQL UI shows composed schema
- [ ] Commit: `feat(scaffold): unified CaseHub server — composed GraphQL schema from all foundation modules`

### Task 2.7: Typed Java client generation

**Repo:** casehub-platform
**Files:**
- Create: `graphql-client/pom.xml` — new module `casehub-graphql-client`
- Create: `graphql-client/src/main/java/io/casehub/client/CaseHubClient.java`
- Create: Generated types from schema

**Interfaces:**
- Consumes: GraphQL schema from Task 2.6
- Produces: `CaseHubClient` — typed Java client with methods for all queries/mutations/subscriptions

**Approach:** SmallRye GraphQL Client (MicroProfile GraphQL Client) — type-safe client from annotated Java interfaces. Selected because:
- Already in the Quarkus/SmallRye ecosystem — zero new dependency trees
- Mirrors server-side `@GraphQLApi` pattern (same annotation model, inverted)
- Native Quarkus integration (dev mode, config, health checks)
- Code-first on both server and client — no schema SDL generation step required

**Key:** The generated client should feel natural to Java developers — method calls, not string queries. Type-safe inputs and outputs.

- [ ] Set up SmallRye GraphQL Client dependency and configuration
- [ ] Define type-safe client interfaces mirroring the composed schema
- [ ] Create `CaseHubClient` with connection configuration
- [ ] Write tests using the client against scaffold
- [ ] Commit: `feat: typed CaseHub GraphQL client`

---

## Phase 3: SPI Callback Mechanism

**Goal:** Enable remote apps to implement platform SPIs via webhook callbacks. When an app connects to a CaseHub server via GraphQL, it can register callbacks for SPIs it implements (WorkerProvisioner, ActionRiskClassifier, etc.). The server calls the app's endpoint when the SPI is invoked.

**Repos:** casehub-engine, casehub-work, casehub-platform, scaffold

**Prerequisite:** Phase 2 complete (GraphQL gateway exists)

**CDI wiring patterns:** The four callback-eligible SPIs use three distinct CDI patterns. Each callback adapter must match its SPI's wiring:

| SPI | Existing impl | CDI pattern | Callback adapter strategy |
|-----|--------------|-------------|--------------------------|
| `WorkerProvisioner` | `NoOpWorkerProvisioner` (`@DefaultBean @ApplicationScoped`) | Default displacement | Adapter is `@ApplicationScoped` — displaces `@DefaultBean` no-op |
| `ActionRiskClassifier` | `ChainedActionRiskClassifier` (collects `@RiskClassifier` beans) | Qualifier chain | Adapter is `@RiskClassifier @ApplicationScoped` — joins the chain |
| `SlaBreachPolicy` | `NoOpSlaBreachPolicy` (`@Unremovable @ApplicationScoped`) | Default displacement (bug: missing `@DefaultBean` — cross-repo fix required) | Prereq: add `@DefaultBean` to `NoOpSlaBreachPolicy` in casehub-work. Adapter is `@ApplicationScoped` — displaces default |
| `WorkerSelectionStrategy` | 3 named strategies: `LeastLoadedStrategy`, `RoundRobinStrategy`, `ClaimFirstStrategy` (all `@Unremovable @ApplicationScoped`, each with `id()`) | Named multi-bean (`Instance<T>`, selected by `id()` at runtime) | Adapter is `@Unremovable @ApplicationScoped` with `id() = "callback"` — joins the collection, selected via configuration |

### Task 3.1: Callback registration API

**Repo:** casehub-platform
**Files:**
- Create: `callback-api/pom.xml` — new submodule `casehub-platform-callback-api`
- Create: `callback-api/src/main/java/io/casehub/platform/callback/CallbackRegistration.java`
- Create: `callback-api/src/main/java/io/casehub/platform/callback/CallbackRegistry.java`
- Create: `callback-api/src/main/java/io/casehub/platform/callback/CallbackInvoker.java`
- Test: `callback-api/src/test/java/`

**Interfaces:**
- Produces: `CallbackRegistration` record — `(String spiName, String callbackUrl, String credentialRef, Duration timeout, Map<String, Object> metadata)`. The `credentialRef` references a token in the platform credential store (encrypted at rest via `casehub-platform-credentials-quarkus`). The `metadata` field carries SPI-specific registration data — e.g., WorkerProvisioner registrations include `{"capabilities": ["cap1", "cap2"]}` for capability-based routing.
- Produces: `CallbackRegistry` SPI — `register(CallbackRegistration)` (idempotent upsert by `spiName + callbackUrl`), `unregister(String spiName, String callbackUrl)`, `findCallbacks(String spiName) → List<CallbackRegistration>`. Registrations have a configurable TTL (default: 5 minutes); clients heartbeat/re-register to keep registrations active. Expired registrations are pruned on next `findCallbacks()` call.
- Produces: `CallbackInvoker` — HTTP client that calls registered callbacks with JSON payload, returns JSON response. Failure semantics: retry up to 3 times with exponential backoff (1s, 2s, 4s) for 5xx and network errors; no retry for 4xx; 429 retried with `Retry-After`. Circuit breaker (via `casehub-engine-resilience`) opens after 5 consecutive failures per callback URL, half-opens after 30s.

**GraphQL mutations for registration:**
```graphql
type Mutation {
  registerCallback(input: CallbackRegistrationInput!): CallbackRegistration!
  unregisterCallback(spiName: String!, callbackUrl: String!): Boolean!
}

type Query {
  registeredCallbacks(spiName: String): [CallbackRegistration!]!
}
```

- [ ] Define callback registration model and registry SPI
- [ ] Implement in-memory and JPA callback registries
- [ ] Implement CallbackInvoker (HTTP client with timeout, retry, error handling)
- [ ] Add GraphQL mutations for registration
- [ ] Write tests
- [ ] Commit: `feat(platform): callback registration API and registry`

### Task 3.2: WorkerProvisioner callback adapter

**Repo:** casehub-engine
**Files:**
- Create: `callback/pom.xml` or add to existing module
- Create: `CallbackWorkerProvisioner.java` — implements `WorkerProvisioner` by calling registered callback

**Interfaces:**
- Consumes: `CallbackRegistry`, `CallbackInvoker` (Task 3.1)
- Consumes: `WorkerProvisioner` SPI (engine api/)
- Produces: `CallbackWorkerProvisioner` — `@ApplicationScoped` bean that invokes registered callbacks when the engine needs to provision a worker

**Pattern:** The callback adapter is the sole `WorkerProvisioner` in centralized mode. It serializes the provision request as JSON, calls the registered app's endpoint, and deserializes the response. If no callback is registered, it throws `ProvisioningException` (same behavior as the `NoOpWorkerProvisioner` it displaces). Embedded and centralized modes are mutually exclusive deployment configurations — in embedded mode, the app provides its own `WorkerProvisioner` directly via CDI; in centralized mode, the callback adapter is the active provisioner.

**Idempotency:** `CallbackWorkerProvisioner` generates a unique idempotency key (UUID) per provision request and includes it in the callback payload. The remote app must deduplicate by this key — if a provision callback times out but the remote side actually provisioned a worker, a retry with the same idempotency key returns the existing worker instead of creating a duplicate. The idempotency key is also included in the `ProvisionResult` for audit linkage.

- [ ] Implement `CallbackWorkerProvisioner` with idempotency key generation
- [ ] Write tests with WireMock for the callback endpoint
- [ ] Commit: `feat(engine): WorkerProvisioner callback adapter`

### Task 3.3: ActionRiskClassifier callback adapter

**Repo:** casehub-engine
**Files:**
- Create: `CallbackActionRiskClassifier.java` — implements `ActionRiskClassifier` via callback

**Wire format:** Define `PlannedActionDto`, `ClassificationContextDto`, and `RiskDecisionDto` record types for callback serialization. `RiskDecision.GateRequired` mapping:
- `Class<?> resolutionType` → fully qualified class name string
- `CandidateSetStrategy` → discriminated union: `{"type": "static", "values": [...]}`
- `QuorumConfig` → `{"required": N, "instances": M}`
- `Duration expiresIn` → ISO-8601 duration string

**Chaining model:** `CallbackActionRiskClassifier` is a single `@RiskClassifier @ApplicationScoped` bean in the `ChainedActionRiskClassifier` chain alongside local CDI classifiers. Internally, it fans out to all registered remote classifiers in parallel (virtual threads), applies most-restrictive-wins across remote results. Remote and local classifiers coexist — this is composition, not fallback.

**Failure semantics:** Any callback failure (timeout, network, deserialization) → return `FAIL_SAFE` GateRequired, consistent with `ChainedActionRiskClassifier`'s existing error handling for classifier exceptions.

**Pattern:** When the engine encounters a `PlannedAction` and needs risk classification:
1. `ChainedActionRiskClassifier` iterates all `@RiskClassifier` beans (local CDI + `CallbackActionRiskClassifier`)
2. `CallbackActionRiskClassifier` checks `CallbackRegistry` for `"ActionRiskClassifier"` registrations
3. If registered: fan out to all registered callbacks in parallel, return most-restrictive result
4. If not registered: return `Autonomous` (no remote classifiers = no additional restriction)

- [ ] Implement `CallbackActionRiskClassifier`
- [ ] Write tests with WireMock
- [ ] Commit: `feat(engine): ActionRiskClassifier callback adapter`

### Task 3.4: SlaBreachPolicy callback adapter

**Repo:** casehub-work
**Files:**
- Modify: `runtime/src/main/java/io/casehub/work/runtime/service/NoOpSlaBreachPolicy.java` — add `@DefaultBean`
- Create: `callback/pom.xml` or add to existing module
- Create: `CallbackSlaBreachPolicy.java` — implements `SlaBreachPolicy` via callback

**Interfaces:**
- Consumes: `CallbackRegistry`, `CallbackInvoker` (Task 3.1)
- Consumes: `SlaBreachPolicy` SPI (`io.casehub.work.api.spi.SlaBreachPolicy`)
- Produces: `CallbackSlaBreachPolicy` — `@ApplicationScoped` bean

**Cross-repo prerequisite:** `NoOpSlaBreachPolicy` is currently `@Unremovable @ApplicationScoped` without `@DefaultBean`. The universal routing strategy design spec documents it as a `@DefaultBean`-only SPI intended for deployment-wide displacement, but the annotation was never added. Add `@DefaultBean` to `NoOpSlaBreachPolicy` so that the callback adapter can displace it — same pattern as `NoOpWorkerProvisioner` in casehub-engine.

**CDI wiring:** `@DefaultBean` displacement (same as Task 3.2). `CallbackSlaBreachPolicy` is `@ApplicationScoped` and automatically displaces the `@DefaultBean NoOpSlaBreachPolicy`.

**Pattern:** When the work module detects an SLA breach:
1. Check `CallbackRegistry` for `"SlaBreachPolicy"` registrations
2. If registered: serialize breach context, call the app's endpoint, deserialize policy response
3. If not registered: return `BreachDecision.Fail("no-sla-breach-policy-configured")` (same as no-op default)

- [ ] Add `@DefaultBean` to `NoOpSlaBreachPolicy` in casehub-work
- [ ] Implement `CallbackSlaBreachPolicy`
- [ ] Write tests with WireMock
- [ ] Commit: `feat(work): SlaBreachPolicy callback adapter`

### Task 3.5: WorkerSelectionStrategy callback adapter

**Repo:** casehub-work
**Files:**
- Create: `CallbackWorkerSelectionStrategy.java` — implements `WorkerSelectionStrategy` via callback

**Interfaces:**
- Consumes: `CallbackRegistry`, `CallbackInvoker` (Task 3.1)
- Consumes: `WorkerSelectionStrategy` SPI (`io.casehub.work.api.spi.WorkerSelectionStrategy`)
- Produces: `CallbackWorkerSelectionStrategy` — `@Unremovable @ApplicationScoped` bean with `id() = "callback"`

**CDI wiring:** Named multi-bean pattern (NOT displacement). `WorkerSelectionStrategy` has three built-in implementations (`LeastLoadedStrategy`, `RoundRobinStrategy`, `ClaimFirstStrategy`), all `@Unremovable @ApplicationScoped`, each with a distinct `id()`. They coexist in an `Instance<WorkerSelectionStrategy>` collection and are selected by configuration at runtime. The callback adapter joins this collection as a 4th strategy — no ambiguity, no displacement. Selection of `"callback"` as the active strategy is done through the existing configuration mechanism (per the universal routing strategy design).

**Pattern:** When the work module selects `"callback"` as the active strategy:
1. Check `CallbackRegistry` for `"WorkerSelectionStrategy"` registrations
2. If registered: serialize candidate list, call the app's endpoint, deserialize `AssignmentDecision`
3. If not registered: return `AssignmentDecision.noChange()` (delegate to assignment service fallback)

- [ ] Implement `CallbackWorkerSelectionStrategy` with `id() = "callback"`
- [ ] Write tests with WireMock
- [ ] Commit: `feat(work): WorkerSelectionStrategy callback adapter`

### Task 3.6: Client-side auto-registration

**Repo:** casehub-graphql-client or casehub-platform
**Files:**
- Create: `CallbackAutoRegistrar.java` — CDI startup bean that discovers local SPI implementations and registers them as callbacks

**Pattern:** When an app starts with the GraphQL client:
1. At `@Startup`, scan for `@ApplicationScoped` beans implementing callback-eligible SPIs
2. For each found: register a callback URL pointing to the app's own callback dispatch endpoint
3. The app exposes a **single static JAX-RS callback dispatch endpoint** (`POST /casehub/callbacks/{spiName}`) that routes incoming callback invocations to the correct local CDI SPI bean based on the `spiName` path parameter. This endpoint is a plain `@Path`-annotated JAX-RS resource discovered at build time — no dynamic endpoint generation or Quarkus extension required.
4. **Startup resilience:** Registration failures are caught and retried with exponential backoff (1s, 2s, 4s, 8s, max 30s) via a background virtual thread. The app starts successfully regardless — failed registrations are retried until success or shutdown. A `/health/ready` check reports `DOWN` while registrations are pending.
5. **Lease renewal:** `CallbackAutoRegistrar` re-registers on a periodic timer (TTL / 2) to keep registrations alive.

**Callback eligibility criteria:** An SPI is callback-eligible when it is (1) stateless and request-scoped — no mutable state across calls, (2) has serializable inputs and outputs — can be marshalled to/from JSON, and (3) has acceptable latency tolerance — network round-trip is acceptable for this operation.

**Callback-eligible SPIs (initial set, per criteria above):**
- `WorkerProvisioner` — stateless provisioning decision, serializable request/response
- `ActionRiskClassifier` — stateless risk assessment, serializable PlannedAction/RiskDecision
- `SlaBreachPolicy` — stateless policy evaluation, serializable breach context/response
- `WorkerSelectionStrategy` — stateless selection, serializable candidate list/selection

- [ ] Implement auto-discovery of SPI beans
- [ ] Implement static callback dispatch endpoint (`CallbackDispatchResource` — single JAX-RS resource that routes by `spiName` to local CDI beans)
- [ ] Implement auto-registration with the CaseHub server at startup
- [ ] Write integration tests
- [ ] Commit: `feat: client-side SPI callback auto-registration`

---

## Cross-Phase: Integration Test Strategy

**Phase 1 → Phase 2:** Verify JSONB payloads render correctly through GraphQL resolvers. Test: create a ledger entry with `domainData`, query it via GraphQL, assert `domainData` fields are present and correctly typed.

**Phase 2 → Phase 3:** Verify callback-registered classifiers chain correctly when invoked through GraphQL mutations. Test: register a remote `ActionRiskClassifier` callback (WireMock), start a case via GraphQL that triggers risk classification, assert the remote classifier's decision is applied alongside local classifiers.

**End-to-end:** Start case via GraphQL → trigger plan item → callback-registered `WorkerProvisioner` provisions worker → worker completes → verify ledger entry with `domainData` → query via GraphQL.

## Cross-Phase: Documentation and Platform Docs

After each phase, update platform documentation:

**Phase 1 complete:**
- Update `docs/platform/persistence.md` — orm.xml mapping pattern, domainData extension field
- Update `docs/repos/casehub-ledger/` — consumer and contributor guides (api/ is now tier-pure)
- Create ADR for the orm.xml approach and domainData extension field

**Phase 2 complete:**
- Update `docs/INDEX.md` — add GraphQL references
- Update `docs/guides/building-apps.md` — add GraphQL as recommended starting point
- Update `docs/guides/building-platform.md` — add graphql/ module tier (presentation adapter, same tier as rest/)
- Create `docs/platform/graphql.md` — custom scalars, schema conventions, resolver execution model (blocking/virtual threads)
- Update `docs/platform/overview.md` — reclassify scaffold as "CaseHub server / unified GraphQL endpoint" (not "gateway" — scaffold is a monolith that includes all modules, not a routing proxy)
- Update scaffold `CLAUDE.md` — new role as centralized server

**Phase 3 complete:**
- Update `docs/guides/building-apps.md` — callback mechanism for SPI extension
- Create `docs/platform/callbacks.md` — SPI callback conventions
- Update `docs/platform/boundary-rules.md` — callback-eligible SPIs

---

## Execution Order Summary

```
Phase 1 (Ledger — independent)  Phase 2 (GraphQL — independent)  Phase 3 (Callbacks — depends on P2)
                                                               
T1.1  Strip JPA from api/       T2.1  Platform GraphQL           T3.1  Callback registry
  ↓                               ↓    (scalars, pagination)       ↓
T1.2  orm.xml in runtime/       T2.2  Engine GraphQL             T3.2  WorkerProvisioner adapter
  ↓                             T2.3  Work GraphQL               T3.3  RiskClassifier adapter
T1.3  domainData field          T2.4  Ledger GraphQL             T3.4  SlaBreachPolicy adapter
  ↓                             T2.5  Qhorus GraphQL             T3.5  WorkerSelectionStrategy adapter
T1.4  Memory + REST               ↓                                ↓
  ↓                             T2.6  Scaffold server            T3.6  Client auto-reg
T1.5  Verify tier purity        T2.7  Typed client
```

**Phase independence:** Phases 1 and 2 are independent and can proceed in parallel. Phase 3 depends on Phase 2 (needs GraphQL endpoint for callback registration). Tasks within a phase are sequential (each depends on the previous). Within Phase 2, Tasks 2.2–2.5 (per-module schemas) can be parallelised.
