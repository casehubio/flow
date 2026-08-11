# Platform Dual-Mode Architecture — Design Decisions

## D1: Three deployment models, layered progression

**Choice:** The platform supports three deployment models — Embedded (library), Centralized (CaseHub server), Federated (multiple servers). Each builds on the previous. Customers start embedded and grow outward.

**Alternatives:**
- Single model (embedded only) — limits enterprise adoption, ops burden for multi-app deployments
- Single model (centralized only) — loses SPI extension, the platform's core value proposition
- All models as equals — too much work for uncertain demand; layered progression focuses investment

**Rationale:** Enterprise customers have different needs at different scales. A single developer wants embedded simplicity. A mid-size team wants centralized ops. A regulated enterprise wants federated isolation. The layered approach serves all without over-building.

**Trade-offs:** Federation (Model C) is deferred until Models A and B are solid. Early customers needing full regulatory isolation between apps must use separate CaseHub servers manually.

**Exploration:** deep-analysis (full platform analysis with 6 subagents across all foundation repos)

**Status:** captured

---

## D2: GraphQL as the remote API, not transparent proxies or command builder

**Choice:** GraphQL with a custom `@export` directive for cross-mutation composition. Standard GraphQL for 80% of operations; `@export` for the 20% that need intermediate result references.

**Alternatives:**
- Typed REST client modules implementing service interfaces — hides the distributed boundary (distributed computing fallacy), two APIs that drift apart
- Custom command builder (Drools ExecutableBuilder pattern) — more powerful (scoped context hierarchy, multi-request state), but custom protocol with no ecosystem, stringly-typed results, maintenance burden, adoption barrier
- Plain REST with batch endpoints — no cross-operation composition, no subscriptions, no schema introspection

**Rationale:** GraphQL provides schema-driven type safety, code generation, built-in subscriptions for events, introspection for discovery, and a massive ecosystem. The `@export` directive covers cross-mutation composition. The command builder is more expressive but the 5% it covers beyond GraphQL doesn't justify a custom protocol.

**Trade-offs:** Multi-request scoped context (Drools CONVERSATION/APPLICATION) is not covered. The platform loses the command builder's full expressiveness.

**Depends on:** D1 (centralized model needs a remote API)

**Exploration:** deep-analysis (steelman + devil's advocate of command builder, GraphQL comparison)

**Status:** captured

---

## D7: Unified code generation — one annotation set, four outputs

**Choice:** A single `@PlatformService` annotation set on service interfaces in api/ modules, with a Quarkus build-time extension that generates REST endpoints, GraphQL resolvers, MCP model providers, and typed Java client from the annotated methods.

**Alternatives:**
- Hand-write each presentation layer separately — triple+ maintenance burden, drift between layers
- Generate only GraphQL, hand-write REST and MCP — still double maintenance for REST
- Schema-first (shared IDL, generate everything including Java interface) — inverts the source of truth, Java interface becomes generated

**Rationale:** The service interface IS the source of truth. REST, GraphQL, and MCP are structural mappings from the same operations. MCP is not "smart state-aware discovery" — it's REST operations encoded as data in a navigable tree instead of as individual MCP tools. All four outputs are derivable from method signatures + minimal annotations (domain, operation type, description).

**Trade-offs:** Custom/composite operations (e.g., trustRoutingProfile batching 4 calls) must be hand-written alongside generated code. The generator is a significant upfront investment. Generated code must match the quality of hand-written code (error handling, ACL, tenancy).

**Exploration:** deep-analysis

**Status:** captured

---

## D8: MCP hierarchy — two hops, domain as the only level

**Choice:** MCP model uses a two-hop hierarchy. Hop 1 returns domain list with summaries and operation counts. Hop 2 returns all operations for a domain with parameter details. The `domain` attribute on `@PlatformService` is the only hierarchy input. Fixed MCP tool set (casehub_model, casehub_action) — tool count never grows with operations.

**Alternatives:**
- Flat (one hop, all operations) — too much data for agent context
- Deep hierarchy (repo → module → package → class → method) — too many hops, exposes implementation topology
- Three-level with sub-groups — premature, add later if a domain exceeds ~20 operations

**Rationale:** Two hops balances discovery efficiency with context cost. The agent reads domains in one call, drills into the relevant domain in one more call, then executes. The hierarchy is about what the agent cares about (domain → operations), not how the code is organized.

**Exploration:** quick (clear consensus after discussion)

**Status:** captured

---

## D3: Embedded API stays as-is — no abstraction tax

**Choice:** The embedded Java API (`@Inject CaseHubRuntime`, `@Inject WorkItemService`, etc.) remains unchanged. No shared interface between embedded and remote. Two APIs for two realities.

**Alternatives:**
- Shared interface that both embedded and remote implement — adds indirection to embedded path for a deployment mode that may not be used
- Replace embedded with GraphQL everywhere — loses type safety, CDI integration, SPI extension

**Rationale:** Don't hide the boundary. Embedded is for in-process use with full CDI, transactions, and SPI extension. GraphQL is for remote use with network-aware composition. Making them share an interface would degrade both.

**Trade-offs:** App code that switches from embedded to remote must change API calls (from injected services to GraphQL client). This is deliberate — the change communicates that the execution model changed.

**Depends on:** D2 (GraphQL is the remote API, not a transparent proxy)

**Exploration:** deep-analysis

**Status:** captured

---

## D4: Scaffold is the CaseHub server product

**Choice:** Scaffold becomes the centralized CaseHub server — the product that people install and build apps against. It embeds all foundation modules, hosts the GraphQL gateway, and provides the management UI via pages workbench.

**Alternatives:**
- Scaffold as reference deployment only — leaves ops teams to build their own server composition
- Each module as a separate microservice — complex ops, loses transactional integrity between modules

**Rationale:** One server to install, one GraphQL endpoint to connect to, one management UI. All cross-module integration works because everything is embedded in one Quarkus process. The complexity of distributed systems stays outside (in the GraphQL protocol), not inside.

**Trade-offs:** Scaffold becomes a critical piece of infrastructure rather than a reference app. It needs production-grade ops: health checks, monitoring, backup, upgrade path.

**Depends on:** D1 (centralized model), D2 (GraphQL gateway)

**Exploration:** deep-analysis

**Status:** captured

---

## D5: GraphQL is the recommended starting point for app builders

**Choice:** New app builders are recommended to start with GraphQL against an existing CaseHub server. Embed modules only when SPI extension is needed.

**Alternatives:**
- Recommend embedded first — simpler for the developer but harder ops (each app runs its own engine/work/ledger)
- Recommend based on app complexity — more nuanced but confusing guidance

**Rationale:** GraphQL gives the fastest time-to-value: connect to a server, hit the schema, start building. Works from Java, TypeScript, Python. No Quarkus project setup, no datasource config, no Flyway. Developers graduate to embedded when they need custom SPIs.

**Trade-offs:** GraphQL-first developers don't get SPI extension. When they need it, they must learn the embedded model — a learning cliff, not a learning curve.

**Depends on:** D2 (GraphQL as remote API), D4 (scaffold as server)

**Exploration:** quick

**Status:** captured

---

## D6: Ledger domain data as JSON payload, not JPA entity inheritance

**Choice:** Replace `LedgerEntry` JPA inheritance with a generic `data: Map<String, Object>` payload field (stored as JSONB in PostgreSQL, sub-document in MongoDB). Domain-specific entry types are pure Java records on the client side that serialize to/from the payload.

**Alternatives:**
- Keep JPA entity inheritance — requires classpath coupling between scaffold and domain apps, Flyway migration per domain type, blocks the centralized model for ledger
- Hybrid delegation (scaffold delegates to app's ledger) — breaks Merkle chain integrity (single sequential writer required)
- Dual ledger (app has its own, scaffold has generic) — double-write consistency problems, two chains

**Rationale:** The ledger SPI is storage-agnostic. JPA inheritance was a persistence concern leaking into the domain model (tier violation). With JSON payload: one writer maintains the Merkle chain, no classpath coupling, no Flyway per domain, works with JPA/MongoDB/in-memory. Domain types are a client-side concern — pure Java records with `toPayload()`/`fromPayload()`.

**Trade-offs:** Loses compile-time typed columns for domain fields. Recoverable with schema validation at write time, typed payload builders, and GraphQL input types. Domain-specific queries use JSONB path expressions instead of typed column queries.

**Migration required:** Existing ledger subtypes must migrate:
- `ComplianceLedgerEntry` (casehub-ops) — move domain fields to JSON payload
- `SponsorNotificationLedgerEntry` (casehub-clinical#21) — move domain fields to payload
- `AgentMessageLedgerEntry` (casehub-qhorus#51) — move domain fields to payload
- Any other `LedgerEntry` subclasses — audit all repos with `ide_find_implementations`
- Flyway migration to add `payload JSONB` column, migrate existing subtype data, drop subtype tables

**Depends on:** D4 (scaffold as centralized server needs storage-agnostic ledger)

**Exploration:** deep-analysis

**Status:** captured
