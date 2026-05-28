# Health Check Endpoints for k8s Liveness and Readiness Probes

**Issue:** #10 (part of epic #1)
**Date:** 2026-05-28
**Status:** Approved

## Goal

Configure health check endpoints using Quarkus SmallRye Health so Kubernetes can manage pod lifecycle and traffic routing via liveness and readiness probes.

## Architecture

### Dependency

Add `quarkus-smallrye-health` to `pom.xml` (version managed by the Quarkus BOM).

### Endpoints

All provided automatically by SmallRye Health:

| Endpoint | Purpose | k8s probe |
|---|---|---|
| `/q/health/live` | Is JVM alive? | Liveness |
| `/q/health/ready` | Can service handle traffic? | Readiness |
| `/q/health` | Aggregate of both | — |

### Components

**1. Liveness** — zero custom code. Quarkus provides a default liveness check when SmallRye Health is on the classpath.

**2. Database readiness** — zero custom code. Quarkus auto-registers a reactive datasource health check when it detects `quarkus-reactive-pg-client` + `quarkus-smallrye-health` together. Validates connection pool health.

**3. CaseEngineHealthCheck** — one custom `@Readiness` check:

```java
package io.casehub.flow.health;

@ApplicationScoped
@Readiness
public class CaseEngineHealthCheck implements HealthCheck {

    private final AtomicBoolean engineReady = new AtomicBoolean(false);

    void onStartup(@Observes StartupEvent event) {
        engineReady.set(true);
    }

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("Case engine")
                .status(engineReady.get())
                .build();
    }
}
```

**Package:** `io.casehub.flow.health`

## Configuration

No custom configuration required beyond adding the dependency. Quarkus defaults:
- Health root path: `/q/health`
- Reactive datasource check: auto-enabled
- Liveness: auto-enabled

## Behavior

| State | Liveness | Readiness | HTTP |
|---|---|---|---|
| JVM starting, before StartupEvent | 200 | 503 (engine: DOWN) | — |
| After startup, DB healthy | 200 | 200 (engine: UP, DB: UP) | — |
| After startup, DB unreachable | 200 | 503 (engine: UP, DB: DOWN) | — |
| JVM crashed / hung | No response | No response | k8s restarts pod |

Health response format (SmallRye default):
```json
{
  "status": "UP",
  "checks": [
    { "name": "Case engine", "status": "UP" },
    { "name": "Reactive PostgreSQL connection health check", "status": "UP" }
  ]
}
```

## Testing Strategy

### Unit tests (`CaseEngineHealthCheckTest.java`)

- Engine not initialized: health check returns DOWN status
- Engine initialized (call `onStartup` directly): health check returns UP status
- Response name is "Case engine"

### Integration tests (`HealthEndpointIT.java`)

Extend `CaseHubIntegrationTestBase` (real Testcontainers DB):
- `GET /q/health/live` returns 200
- `GET /q/health/ready` returns 200 (DB + engine both UP)
- Readiness response body contains "Case engine" with status "UP"
- Readiness response body contains database check with status "UP"

### Not tested

DB-down scenario in integration tests — would require killing the testcontainer mid-test, which is fragile. The Quarkus built-in datasource check is well-tested upstream.

## Files Changed

| File | Change |
|---|---|
| `pom.xml` | Add `quarkus-smallrye-health` dependency |
| `src/main/java/io/casehub/flow/health/CaseEngineHealthCheck.java` | New: custom readiness check |
| `src/test/java/io/casehub/flow/health/CaseEngineHealthCheckTest.java` | New: unit tests |
| `src/test/java/io/casehub/flow/rest/HealthEndpointIT.java` | New: integration tests |
