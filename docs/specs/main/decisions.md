# Scaffold Console — Design Decisions

## D1: Audience

**Choice:** Both developer console and ops dashboard
**Alternatives:**
- Developer-only console — simpler scope, but ops teams have no generic UI
- Ops-only dashboard — misses the getting-started / debugging use case
**Rationale:** Scaffold is the out-of-box deployment experience. Both audiences need it when no domain app is present.
**Trade-offs:** Broader scope means more tabs/views to compose
**Exploration:** quick
**Status:** captured

## D2: Frontend stack

**Choice:** Lit + TypeScript + esbuild + Quinoa (same as devtown and clinical)
**Alternatives:**
- React SPA — wider ecosystem but inconsistent with casehub platform
- Quarkus Qute + HTMX — simpler but can't reuse blocks-ui web components
**Rationale:** Ecosystem consistency. blocks-ui components are Lit web components consumed via WebJars. devtown and clinical prove the pattern works.
**Trade-offs:** Smaller component ecosystem than React, but irrelevant since blocks-ui provides everything needed
**Exploration:** quick
**Status:** captured

## D3: Scope

**Choice:** Full platform surface — all blocks-ui components composed into tabs
**Alternatives:**
- Core ops views only (cases, workers, events, work items)
- Core + orchestration (above plus DAG, plan model, execution monitor)
**Rationale:** blocks-ui already has the components extracted and tested. Composition cost per-component is minimal (registerPanel + hostPanel). No reason to leave capabilities out.
**Trade-offs:** More views to maintain, but each is thin composition code
**Exploration:** quick
**Status:** captured

## D4: Navigation model

**Choice:** Tabbed layout (like devtown)
**Alternatives:**
- Sidebar navigation (tree, like clinical)
- IDE-style dockable workbench (pages' dockWorkbench)
**Rationale:** Consistent with devtown. Tabs work well when the number of top-level concerns is bounded (10 tabs). Sidebar would be warranted if the hierarchy deepened.
**Trade-offs:** Limited nesting depth. Sub-navigation within tabs handled by split-workbench pattern.
**Exploration:** quick
**Status:** captured

## D5: Module structure

**Choice:** Separate Maven module (scaffold-web) alongside existing scaffold
**Alternatives:**
- Maven profile (include/exclude Quinoa) — simpler but mixes frontend and backend in one module
- Always included — forces frontend deps on headless deployments
**Rationale:** Clean separation. Headless deployments exclude the module entirely. scaffold becomes a multi-module parent. scaffold-web depends on scaffold (backend).
**Trade-offs:** Scaffold goes from single-module to multi-module project
**Exploration:** quick
**Status:** captured

## D6: Data flow

**Choice:** Direct API calls first, BFF endpoints only when no existing endpoint provides the needed data
**Alternatives:**
- Direct-only — simpler but may hit walls with aggregation views
- BFF layer from day one — more flexibility but premature abstraction
**Rationale:** The REST surface is already rich (engine-rest, work-rest, ops, iot). Most views are single-endpoint consumers. BFF adds code for no benefit until a view genuinely needs cross-API aggregation.
**Trade-offs:** Some views may need BFF later, requiring a retrofit
**Exploration:** quick
**Status:** captured

## D7: Composition approach

**Choice:** Pure pages DSL composition — zero new Lit components
**Alternatives:**
- Hand-authored Lit components per tab — bypasses pages, inconsistent with ecosystem
- Thin coordinator components — unnecessary given pages' hostPanel + selection-topic eventing
**Rationale:** pages provides the composition model (page, tabs, hostPanel, registerPanel, rest sources, lookup pipelines). blocks-ui provides the domain components. scaffold-web only needs to wire them together. devtown and clinical prove this pattern scales.
**Trade-offs:** Coupled to pages DSL. If pages can't express a layout, we'd need to extend pages rather than work around it in scaffold.
**Exploration:** quick
**Status:** captured

## D8: Tab structure

**Choice:** 10 tabs covering the full platform + OPS surface
**Alternatives:**
- Fewer tabs with sub-navigation — more compact but hides features
- More granular tabs — would exceed comfortable tab count
**Rationale:** Each tab maps to a distinct operational concern with its own blocks-ui workbench. 10 is manageable in a tab bar.
**Trade-offs:** Tab bar may feel crowded on small screens. Could collapse to hamburger menu.
**Tabs:** Cases, Work Items, Queues, Orchestration, Trust & Audit, Operations, IoT, Notifications, Sessions, System
**Exploration:** quick
**Status:** captured

## D9: Cross-tab navigation (horizontal traversals)

**Choice:** URL hash routing — URL encodes current tab + selected entity. Browser back works, links are shareable.
**Alternatives:**
- Global event bus — simpler but no URL state, no deep-linking, no back button
- Defer to v2 — each tab works independently first
**Rationale:** The data model is a graph. Cases link to work items, work items sit in queues, workers have trust scores. Users need to follow these links naturally. Pages' selection-topic handles intra-tab coordination (list → detail). Cross-tab deep-linking (click case ID in Work Items → jump to Cases tab) uses URL hash state. Standard SPA pattern.
**Trade-offs:** URL hash routing adds a thin navigation layer on top of pages' tab switching. Must coordinate hash changes with pages' internal tab state.
**Depends on:** D4 (tabbed navigation), D7 (pages composition)
**Exploration:** quick
**Status:** revised (review round 1 — clarified mechanism, removed contradiction)

## D10: OPS/IoT tab conditionality

**Choice:** Runtime detection via a `/api/modules` endpoint that reports available modules. One build artifact — tabs appear/hide based on what's deployed.
**Alternatives:**
- Build-time Maven profiles — different builds for different deployments
- Always show all tabs (with "module not available" placeholder)
**Rationale:** Runtime detection means one artifact works everywhere. The console fetches `/api/modules` on startup, registers tabs for available modules only. Adding OPS to the classpath is sufficient — no rebuild of scaffold-web needed.
**Trade-offs:** Requires a module registry endpoint on the backend. Tabs that reference unavailable REST endpoints will fail gracefully if a module is removed without restarting the console.
**Depends on:** D5 (separate module), D8 (tab structure)
**Exploration:** quick
**Status:** revised (review round 1 — resolved mechanism)

## D11: Authentication

**Choice:** Platform OIDC with Quarkus dev-mode bypass for fast click login during development
**Alternatives:**
- Auth-free v1 — fastest path but limits production use of ops/audit views
- OIDC-only (no dev bypass) — slower development iteration
**Rationale:** scaffold already has `casehub-platform-oidc` and `AclRequestFilter`. An ops dashboard exposing trust scores and audit trails requires auth in production. Quarkus dev services provide a test identity provider with click-to-login for fast development. Dev profile uses `quarkus.oidc.devservices.enabled=true`; prod profile uses real OIDC provider.
**Trade-offs:** None significant — this is the standard Quarkus pattern. Role-gated tabs (D8) use the JWT claims to show/hide admin-only views.
**Depends on:** D1 (audience includes ops), D8 (tab structure)
**Exploration:** quick
**Status:** captured
