---
title: "Your MCP Server Has Too Many Tools"
date: 2026-08-11
type: article
subtype: explanation
tags: [mcp, architecture, ai-agents, quarkus]
---

# Your MCP Server Has Too Many Tools

Every MCP tool you register costs context. The tool name, description, parameter schema, and type definitions all land in the agent's prompt before it reasons about your request. For a server with ten tools this is fine. For a platform with fifty operations across six domains, you have burned thousands of tokens before the agent has even started thinking.

I hit this building CaseHub. The platform has a case engine, a work-item inbox, an audit ledger, a communication mesh, agent identity, and desired-state management. Each domain has 8-15 operations. Expose them all as individual MCP tools and the agent stares at a wall of 60+ tool descriptions, most irrelevant to the task at hand. Tool selection accuracy drops. Context is consumed by descriptions the agent will never call.

The fix is straightforward: stop encoding operations as tools. Encode them as data.

## The pattern: model + action

Instead of N tools (one per operation), you register two:

```java
@McpServer("casehub")
@ApplicationScoped
public class CaseHubMcpTools {

    @Inject Instance<ModelProvider> providers;
    @Inject ActionDispatcher dispatcher;

    @McpTool(description = "Navigate CaseHub capabilities")
    public ModelResponse model(@Nullable String path) {
        if (path == null) {
            return ModelResponse.domains(providers);
        }
        return ModelResponse.domain(path, providers);
    }

    @McpTool(description = "Execute a CaseHub operation")
    public ActionResponse action(String domain, String operation,
                                 Map<String, Object> params) {
        return dispatcher.dispatch(domain, operation, params);
    }
}
```

Two tools. The entire platform surface is accessible through them.

## Two hops to anything

The agent discovers what is available through the model, not through tool schemas:

**Hop 1** — what domains exist:
```json
// model()
{
  "domains": {
    "engine": { "summary": "Case lifecycle and coordination", "operations": 12 },
    "work":   { "summary": "Work items, inbox, delegation", "operations": 15 },
    "ledger": { "summary": "Audit trail and trust scores", "operations": 8 },
    "qhorus": { "summary": "Channels, messages, presence", "operations": 10 }
  }
}
```

Small payload. The agent reads this and knows which domain to drill into.

**Hop 2** — what operations a domain offers:
```json
// model("engine")
{
  "domain": "engine",
  "operations": [
    {
      "name": "startCase",
      "type": "mutation",
      "description": "Start a new case instance",
      "params": {
        "namespace": "String",
        "name": "String",
        "inputData": "JSON"
      },
      "returns": "UUID"
    },
    {
      "name": "suspendCase",
      "type": "mutation",
      "params": { "caseId": "UUID" }
    },
    {
      "name": "listCases",
      "type": "query",
      "params": { "status": "String?", "namespace": "String?" },
      "returns": "CasePage"
    }
    // ... all 12 engine operations
  ]
}
```

The agent now has full parameter detail for the domain it cares about. It calls `action("engine", "startCase", { "namespace": "aml", "name": "sar-investigation" })` and gets the result.

Two hops. Two tools. The agent's context contains only the domain it is working with, not the full platform catalog.

## Why flat tool lists fail at scale

Consider a coordinator agent managing a case that involves creating work items, querying trust scores, and sending channel messages. With flat tools:

```
Available tools (62):
  engine_startCase, engine_suspendCase, engine_resumeCase, engine_cancelCase,
  engine_signalCase, engine_listCases, engine_getCaseById, engine_getCaseContext,
  engine_getCaseDefinitions, engine_getCasePlanItems, engine_getCaseGoals,
  engine_getCaseEvents,
  work_createWorkItem, work_claimWorkItem, work_startWorkItem,
  work_completeWorkItem, work_rejectWorkItem, work_delegateWorkItem,
  work_suspendWorkItem, work_resumeWorkItem, work_cancelWorkItem,
  work_escalateWorkItem, work_getWorkItems, work_getWorkItemById,
  work_getInbox, work_getInboxSummary,
  ledger_appendEntry, ledger_getEntries, ledger_getEntry,
  ledger_getTrustScore, ledger_getCapabilityScore, ledger_getRoutingProfile,
  ledger_verifyMerkle, ledger_createAttestation,
  qhorus_createChannel, qhorus_deleteChannel, qhorus_pauseChannel,
  // ... 28 more
```

Every one of those tool definitions — name, description, parameters, types — is in the agent's context window for every turn. The agent working on a trust score query is paying the context cost of channel management and work item delegation tools it will never call.

With the hierarchical model, the same agent sees:

```
Available tools (2):
  casehub_model(path?)
  casehub_action(domain, operation, params)
```

It calls `model()`, sees four domains, calls `model("ledger")`, gets the 8 ledger operations, calls `action("ledger", "getRoutingProfile", { "actorId": "agent-1", "capability": "sar-filing" })`. Three calls, but only the ledger operations consumed context. The other 54 operations were never loaded.

## The ModelProvider SPI

Each domain contributes its operations through a provider interface:

```java
public interface ModelProvider {
    String domain();
    String summary();
    List<OperationDescriptor> operations();
}
```

Providers are CDI beans, discovered at startup. The `model` tool aggregates them:

```java
@McpTool
public ModelResponse model(@Nullable String path) {
    if (path == null) {
        // Hop 1: domain summaries
        Map<String, DomainSummary> domains = new LinkedHashMap<>();
        for (ModelProvider p : providers) {
            domains.put(p.domain(), new DomainSummary(
                p.summary(), p.operations().size()));
        }
        return new ModelResponse(domains);
    }
    // Hop 2: operations for one domain
    for (ModelProvider p : providers) {
        if (p.domain().equals(path)) {
            return new ModelResponse(p.domain(), p.operations());
        }
    }
    throw new NotFoundException("Unknown domain: " + path);
}
```

The `action` tool dispatches to the service method:

```java
@McpTool
public ActionResponse action(String domain, String operation,
                              Map<String, Object> params) {
    ModelProvider provider = resolve(domain);
    return provider.invoke(operation, params);
}
```

Multiple `@PlatformService` interfaces with the same domain merge at runtime. If `CaseHubRuntime` and `CaseDefinitionRegistry` both declare `domain = "engine"`, their operations appear together under the `engine` domain. The agent sees one domain, not two classes.

## Different apps, different model trees

The model tree reflects what is actually deployed, not a static catalog. This is important when the same platform serves different applications.

A financial compliance app that embeds engine, work, and ledger:
```json
// model()
{ "engine": { "operations": 12 }, "work": { "operations": 15 },
  "ledger": { "operations": 8 }, "aml": { "operations": 5 } }
```

A chat application that embeds only the communication mesh:
```json
// model()
{ "qhorus": { "operations": 10 }, "chat": { "operations": 3 } }
```

Same two MCP tools. Different model trees. The providers are CDI beans — only the ones on the classpath contribute. No configuration, no registration. Add a module to the classpath and its operations appear in the model.

## Generating providers from annotated interfaces

Hand-writing a ModelProvider per domain is busywork. The operations are already defined on the service interface — the provider is a mechanical projection of method names, parameter types, and return types.

Annotate the interface:

```java
@PlatformService(domain = "engine", summary = "Case lifecycle and coordination")
public interface CaseHubRuntime {

    @Mutation(description = "Start a new case instance")
    UUID startCase(CaseDefinition definition, Object inputData);

    @Query(description = "Query case context by path")
    Object query(UUID caseId, String path);

    @Mutation(description = "Suspend a running case")
    void suspendCase(UUID caseId);
}
```

A build-time processor generates the ModelProvider. It also generates REST endpoints and GraphQL resolvers from the same annotations — one source of truth, multiple presentation layers. The annotation model lives in the pure Java API module (Tier 1, no framework dependencies). The generator is a Quarkus deployment module.

Adding a new operation to the platform becomes: add a method to the service interface, annotate it with `@Query` or `@Mutation`. REST endpoint, GraphQL field, and MCP action descriptor all appear at the next build. No hand-written adapter classes, no registration, no drift between layers.

## When to add a dedicated tool

The two-tool pattern is not an absolute. Some operations justify their own MCP tool when:

- **The operation is called frequently and the generic dispatch adds friction.** If the agent calls `startCase` ten times a session, promoting it to a dedicated `casehub_startCase` tool saves the `domain` and `operation` boilerplate on every call.

- **The parameter shape is complex and benefits from schema validation.** The MCP protocol validates tool parameters against the schema. A dedicated tool gets schema-level validation; the generic `action` tool accepts `Map<String, Object>` and validates at dispatch time.

- **The operation has distinct semantics that an LLM selects better from the tool name.** If the agent struggles to find the right operation string for `action`, a named tool removes ambiguity.

The principle: every tool must justify its context cost. Start with two. Add dedicated tools only when you have evidence that the generic dispatch is causing friction — not because the operation is important.

## Adapting the pattern

The CaseHub implementation uses Quarkus, CDI, and SmallRye MCP server. The pattern itself is framework-agnostic:

1. **Define a provider interface** for domain registration
2. **Build two tools** — model (returns the catalog) and action (dispatches operations)
3. **Organise by domain** — the only hierarchy level the agent needs to navigate
4. **Keep hops to two** — domain list, then domain detail. More depth adds round-trips without meaningful filtering
5. **Generate providers from metadata** — annotations, decorators, or whatever your language provides for build-time introspection

The point is not the specific technology. The point is separating capability discovery from capability execution. Tools handle execution. Data handles discovery. The agent reads the catalog, picks what it needs, and calls the generic executor. The tool count stays fixed as the platform grows.
