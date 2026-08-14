# Scaffold Console — Design Spec

## Overview

Add a generic platform console to scaffold — a web UI that composes existing casehub-pages and blocks-ui components into a tabbed dashboard covering the full platform surface. This is the out-of-box experience when deploying CaseHub without a domain-specific application.

Scaffold becomes a multi-module Maven project. The new `scaffold-web` module is optional — headless deployments exclude it entirely.

## Audience

Both developer console (explore definitions, trigger test cases, inspect state) and ops dashboard (monitor running cases, workers, queues, SLA breaches). The same UI serves both — the tab structure covers the full operational surface.

## Architecture

### Module Structure

```
scaffold/
  pom.xml                        ← parent POM (packaging: pom)
  scaffold-backend/              ← renamed from current root module
    pom.xml                      ← existing scaffold (packaging: quarkus)
    src/main/java/               ← existing Java sources
    src/main/resources/          ← existing application.properties
  scaffold-web/                  ← new module
    pom.xml                      ← Quinoa + casehub-blocks-ui-npm dependency
    src/main/webui/              ← frontend source (esbuild via Quinoa)
      package.json
      src/
        index.ts                 ← composition root
        datasets.ts              ← REST source bindings
        navigation.ts            ← URL hash routing
        views/
          cases.ts
          work-items.ts
          queues.ts
          orchestration.ts
          trust-audit.ts
          operations.ts          ← conditional (OPS)
          iot.ts                 ← conditional (IoT)
          notifications.ts
          sessions.ts
          system.ts
```

scaffold-web depends on scaffold-backend. The parent POM coordinates both modules.

### Frontend Stack

Lit + TypeScript + esbuild + Quinoa. Identical to devtown and clinical.

- **casehub-pages** provides the composition DSL: `page()`, `tabs()`, `columns()`, `rows()`, `hostPanel()`, `registerPanel()`, `rest()` data sources, `lookup()` pipelines, `metric()`, charts
- **blocks-ui** provides ~90 platform-aware web components consumed via the `casehub-blocks-ui-npm` Maven artifact
- **Zero new Lit components** — scaffold-web is purely compositional

### Dependency Chain

```
casehub-blocks-ui-npm (SNAPSHOT JAR with packed npm tarballs)
  ↓ maven-dependency-plugin unpacks to .casehub-packages/
package.json (file: dependencies on @casehubio/pages-* + @casehubio/blocks-ui-*)
  ↓ npm install + esbuild
Quinoa bundles to META-INF/resources/
  ↓ Quarkus serves
Browser
```

## Data Flow

### Direct API Calls

The frontend calls existing REST endpoints directly. No BFF layer.

```typescript
// Core platform
rest("cases", "/api/v1/cases")
rest("case-definitions", "/api/v1/definitions")
rest("workers", "/api/v1/workers")
rest("work-items", "/workitems", { refreshTime: 5000 })
rest("queues", "/queues")
rest("audit", "/audit")
rest("notifications", "/notifications/stream", { type: "sse" })
rest("modules", "/api/modules")

// Conditional (OPS)
rest("applications", "/api/applications")
rest("approvals", "/api/approvals")
rest("reconciliation", "/api/reconciliation")

// Conditional (IoT)
rest("devices", "/api/devices")
rest("device-stream", "/api/devices/stream", { type: "sse" })
rest("situations", "/api/situations")
rest("providers", "/api/providers")
```

Real-time views use SSE sources or polling with `refreshTime`. BFF endpoints are added only when a view genuinely needs data that no single existing endpoint provides.

### Intra-Tab Data Flow

Pages' `selection-topic` eventing handles coordination within a tab. Selecting a row in a list emits `{topic}:selected`, which drives detail panels and `hostPanel` template props like `#{row.caseId}`.

### New Backend Endpoint

One new REST resource in scaffold-backend:

`GET /api/modules` returns the set of modules available on the classpath:

```json
{ "modules": ["engine", "work", "ops", "iot", "ledger", "planning"] }
```

Implementation: a CDI bean that probes for key classes via `Instance<>` injection.

## Tab Structure

10 tabs. The first 5 and last 3 are always present. Operations and IoT are conditional on module availability.

### Cases

**Component:** `case-explorer` with all 5 presets (`caseInstanceType`, `workerType`, `caseDefinitionType`, `channelType`, `gateType`).

**Layout:** The case-explorer handles its own layout internally — entity type tabs along the top, list/tree toggle, entity-list or entity-tree on the left, entity-detail on the right, entity-command-bar for actions. Relationships enable drill-down (case → workers → sub-cases) with breadcrumb navigation back.

**Data:** Each preset's `listEndpoint` and `detailEndpoint` map to the engine/work REST surface.

### Work Items

**Component:** `work-item-workbench` via `hostPanel`.

**Layout:** Self-contained — inbox (3-tab perspective: my tasks, available, completed), detail panel with activity/relations tabs, action bar.

**Data:** `endpoint` + `identity` (from JWT claims). SSE for real-time updates.

### Queues

**Components:** `kpi-metric-row` (top), `split-workbench` + `list-pane` + `detail-pane` (body).

**Layout:** KPI row showing queue health metrics. Below: queue list on the left, selected queue's summary, trend chart (pages `barChart`), and SSE event stream on the right.

**Data:** `/queues` for list, `/queues/{id}/summary` and `/queues/{id}/trend` for detail, `/queues/{id}/events` for SSE stream.

### Orchestration

**Components:** Pages `dataTable` (case selector) + `orchestration-workbench`, `dag-viewer`, `plan-model-dashboard`, `decomposition-tree`, `plan-item-tree`.

**Layout:** Two-level. Top: data table of active cases filtered by status. Bottom: selected case's orchestration views in tabs — execution monitor, DAG visualization, plan model, decomposition tree, plan item tree.

**Data:** Case list from `/api/v1/cases`, plan endpoints from `/api/v1/cases/{caseId}/plan/*`. Driven by `#{row.caseId}` template binding from the case selector.

### Trust & Audit

**Components:** `split-workbench` + `list-pane` (actor list) + `detail-pane` with `trust-workbench`, `audit-trail-viewer`, `compliance-summary`, `routing-rationale`.

**Layout:** Actor list on the left (from `/workitems/actors`). Selected actor's trust scores, audit trail, compliance posture, and routing rationale in tabbed detail on the right.

**Data:** `/workitems/actors` for list, `/workitems/actors/{actorId}/trust` for trust, `/audit` for trail, compliance and rationale via their respective endpoints.

### Operations (conditional — requires OPS module)

**Components:** `kpi-metric-row`, `split-workbench` + `list-pane` + `detail-pane` with `approval-gate`, `dag-viewer`, `compliance-summary`.

**Layout:** KPI row for deployment health metrics. Application list on the left from `/api/applications`. Selected application's detail tabs on the right: deployment history, pending approvals (`approval-gate`), desired-state graph (`dag-viewer` with reconciliation state decorations), compliance posture (`compliance-summary`).

**Data:** `/api/applications` for list, `/api/applications/{id}/deployments` for history, `/api/approvals` for gates, `/api/reconciliation` for desired-state graph state.

### IoT (conditional — requires IoT module)

**Components:** `split-workbench` + `list-pane` (device list with `status-badge` column renderers) + `detail-pane` with state history, command dispatch, situation list.

**Layout:** Device list on the left from `/api/devices` with status badges showing device state. Selected device's detail tabs on the right: state history (pages `dataTable`), command dispatch panel, related situations from `/api/situations`.

**Data:** `/api/devices` for list, SSE `/api/devices/stream` for real-time state, `/api/situations` for situation management, `/api/providers` for provider status.

### Notifications

**Components:** `notification-inbox` + `notification-preferences` via `hostPanel`.

**Layout:** Two sub-tabs: inbox (bell + notification list with subscription management) and preferences (channel preferences, mute/snooze controls).

**Data:** `/notifications/*` for inbox, SSE `/notifications/stream` for real-time, `/subscriptions/*` for preference management.

### Sessions

**Component:** `session-workbench` via `hostPanel`.

**Layout:** Self-contained — session list on the left, selected session's terminal, git status, health, and SSE event stream on the right.

**Data:** `endpoint` prop pointing to `/api/sessions`.

### System

**Components:** `case-definition-browser`, `preferences-editor`, `kpi-metric-row`.

**Layout:** Three sub-tabs. Definitions: browse registered case definitions from `/api/v1/definitions`. Preferences: scope-aware editor (system > tenant > team > user). Health: KPI metric row showing system health indicators.

**Data:** `/api/v1/definitions`, preferences API, health check endpoints.

## Cross-Tab Navigation

### URL Hash Routing

A thin `navigation.ts` module manages URL hash state:

```
#/cases/abc-123           → Cases tab, case abc-123 selected
#/work-items              → Work Items tab, no selection
#/trust-audit/worker-42   → Trust & Audit tab, actor worker-42 selected
```

On hash change: (1) switch the active tab via pages' tab API, (2) emit a `{topic}:selected` event with the entity ID so the target tab's components load the right data.

### Entity Links

Entity ID references rendered in any tab (e.g., a case ID column in the work items list) are `<a href="#/cases/{id}">` anchors — standard HTML, no framework coupling. Browser back button works naturally.

### Relationship Traversals

Common cross-tab paths:

| From | Link | To |
|------|------|----|
| Work Items → case ID | `#/cases/{caseId}` | Cases tab, case selected |
| Cases → worker | `#/trust-audit/{workerId}` | Trust & Audit tab, actor selected |
| Cases → queue | `#/queues/{queueId}` | Queues tab, queue selected |
| Queues → work item | `#/work-items/{itemId}` | Work Items tab, item selected |
| Operations → approval | `#/work-items/{gateId}` | Work Items tab, gate selected |
| Orchestration → case | `#/cases/{caseId}` | Cases tab, case selected |

## Module Detection

### Runtime Detection

On startup, the console fetches `GET /api/modules`. Based on the response, conditional tabs are registered:

```typescript
const modules = await fetch("/api/modules").then(r => r.json());
const conditionalTabs = [
  ...(modules.includes("ops") ? [["Operations", operationsView]] : []),
  ...(modules.includes("iot") ? [["IoT", iotView]] : []),
];
```

### Backend Implementation

`ModuleRegistryResource` in scaffold-backend — a `@Path("/api/modules")` resource that probes for module marker classes via CDI `Instance<>`. Each module exposes a marker interface (e.g., `OpsModuleMarker`, `IoTModuleMarker`). If the `Instance` is resolvable, the module is present.

## Authentication

### Production

Platform OIDC. The console redirects to the configured OIDC provider. REST calls carry JWT via `Authorization` header. `AclRequestFilter` (already in scaffold-backend) enforces role-based access on all API calls.

### Development

`quarkus.oidc.devservices.enabled=true` in the dev profile provides a test identity provider with click-to-login. No credential management needed during development.

### Role-Gated Tabs

JWT claims include roles. The console can hide tabs (e.g., System admin views) for non-admin users. This is a UI convenience — the backend enforces access regardless via `AclRequestFilter`.

## Testing Strategy

### Frontend

- **Unit tests:** Not required for v1 — the view files are pure composition (no logic to test). If custom navigation logic grows complex, add tests for `navigation.ts`.
- **Integration:** Start scaffold with Quinoa dev mode, verify all tabs render, verify cross-tab navigation works. Manual verification initially.

### Backend

- **ModuleRegistryResource:** Unit test that verifies module detection for present/absent modules.
- **Existing tests:** All existing scaffold-backend tests continue to pass unchanged.

## Migration Path

### scaffold → multi-module

1. Create parent `pom.xml` (packaging: pom, GAV: `io.casehub:scaffold`) with two modules
2. Move existing `src/`, `pom.xml` content into `scaffold-backend/`
3. Backend artifact becomes `io.casehub:scaffold-backend` (packaging: quarkus)
4. New web artifact is `io.casehub:scaffold-web` (packaging: jar, Quinoa bundles into META-INF/resources)
5. Verify `mvn install` from the parent builds both modules
6. Verify headless mode: `mvn install -pl scaffold-backend` skips the web module

### Future Extensibility

- **New blocks-ui OPS components:** The Operations tab composes existing blocks-ui components (`approval-gate`, `dag-viewer`, `compliance-summary`, `kpi-metric-row`) that are available now. OPS-specific components (desired-state graph decorations, reconciliation status views) are being built separately. As they land, scaffold-web adds `registerPanel()` + `hostPanel()` calls. The Operations tab works with current components and improves as new ones arrive — it is not blocked.
- **Pages tab-switching API:** Cross-tab navigation (D9) requires programmatic tab switching. Verify that devtown already uses this pattern before implementing `navigation.ts`. If not, a small pages framework addition may be needed.
- **BFF endpoints:** If a view needs aggregated data, add a REST resource in scaffold-backend. The frontend switches from `rest()` source to the BFF endpoint.
- **Domain app override:** A domain app (devtown, clinical) can include scaffold-web as a dependency and override/extend the tab structure if needed. Not a v1 concern.
