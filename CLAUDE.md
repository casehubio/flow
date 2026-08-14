# CLAUDE.md

**Name:** scaffold

## Project Type

**Type:** java

---

## Repository Role

Deployable microservice for running CaseHub's goal-driven coordination engine — shared context, autonomous workers, milestones, goals, and event-driven execution. Provides a standalone Quarkus application with REST endpoints wrapping `casehub-engine`.

**GitHub:** [casehubio/scaffold](https://github.com/casehubio/scaffold)
**Tier:** Integration (pending platform coherence analysis — see casehubio/parent#78)

---

## Build Commands

```bash
# Build all (backend + web console)
mvn --batch-mode install

# Skip tests
mvn --batch-mode install -DskipTests

# Build backend only (headless — no web console)
mvn --batch-mode install -pl scaffold-backend -am -DskipTests
```

---

## Work Tracking

**Issue tracking:** enabled

All implementation work must be linked to a GitHub issue:
- Before starting implementation, create an epic + child issues (or confirm an existing issue)
- All commits reference an issue: `Refs #N` (work in progress) or `Closes #N` (completes the issue)
- When staged changes span multiple concerns, split into separate commits with separate issue references

**Automatic behaviors:**
- Phase 1 (Pre-Implementation): Create epic + child issues before coding begins
- Phase 2 (Task Intake): Detect cross-cutting concerns and suggest breaking into separate issues
- Phase 3 (Pre-Commit): Verify issue linkage; suggest commit splits when staged changes span multiple concerns

**Repository:** casehubio/scaffold

---

## Development Workflow

Before designing: `superpowers:brainstorming`
Before implementing: `superpowers:test-driven-development`
For all Java work: `java-dev`
Before committing: `superpowers:requesting-code-review`

---

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | project     | lands in `docs/adr/` — promoted at epic close |
| specs      | project     | lands in `docs/specs/` — promoted at epic close |
| blog       | project     | lands in `docs/blog/` — promoted at work end |
| plans      | workspace   | stay in workspace permanently |
| design     | workspace   | epic journal stays in workspace |
| snapshots  | workspace   | stay in workspace permanently |
| handover   | workspace   | |

---

## Platform Context

This repo is one component of the casehubio multi-repo platform. **Before implementing anything — any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol.**

> **Platform docs:** Local paths use `../parent/docs/` as root. If a path doesn't exist, the parent repo isn't cloned locally — fetch from `https://raw.githubusercontent.com/casehubio/parent/main/docs/<path>` instead.

The protocol asks: Does this already exist elsewhere? Is this the right repo for it? Does this create a consolidation opportunity? Is this consistent with how the platform handles the same concern in other repos?

**Platform architecture (fetch before any implementation decision):**
```
https://raw.githubusercontent.com/casehubio/parent/main/docs/PLATFORM.md
```

**Other repo deep-dives** (fetch the relevant ones when your implementation touches their domain):
- casehub-engine: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-engine.md`
- casehub-ledger: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-ledger.md`
- casehub-work: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-work.md`
- casehub-qhorus: `https://raw.githubusercontent.com/casehubio/parent/main/docs/repos/casehub-qhorus.md`

---

## Ecosystem Conventions

All casehubio projects align on these conventions:

**Quarkus version:** `version.quarkus.platform` in root `pom.xml`, currently `3.32.2`. All ecosystem projects must match. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:** Root `pom.xml` has `<repositories>` with `id=github` pointing to `https://maven.pkg.github.com/casehubio/*`. CI uses `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

---

## IntelliJ MCP Tools

Two IntelliJ MCP servers are available (`mcp__intellij__*` and `mcp__intellij-index__*`).
Before using Bash tools, check whether the operation can be performed via IntelliJ — it is
often more correct, faster, and less error-prone (symbol lookup, rename refactoring, diagnostics,
file search). Verify both are responsive at session start; stop and report to the user if either
is unavailable.

## Writing Style Guide

**The writing style guide at `~/claude-workspace/writing-styles/blog-technical.md` is mandatory for all blog and diary entries.** Load it in full before drafting. Complete the pre-draft voice classification (I / we / Claude-named) before generating any prose. Do not show a draft without verifying it against the style guide.
