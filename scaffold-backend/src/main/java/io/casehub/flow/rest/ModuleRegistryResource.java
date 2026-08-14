package io.casehub.flow.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/api/modules")
@Produces(MediaType.APPLICATION_JSON)
public class ModuleRegistryResource {

    private final List<String> modules;

    @Inject
    public ModuleRegistryResource() {
        var detected = new ArrayList<String>();
        probeClass("io.casehub.api.engine.CaseHubRuntime", "engine", detected);
        probeClass("io.casehub.work.runtime.service.WorkItemService", "work", detected);
        probeClass("io.casehub.engine.plan.DagPlan", "planning", detected);
        probeClass("io.casehub.ledger.LedgerService", "ledger", detected);
        probeClass("io.casehub.ops.app.OpsApplication", "ops", detected);
        probeClass("io.casehub.iot.webapp.IoTApplication", "iot", detected);
        this.modules = List.copyOf(detected);
    }

    @GET
    public Map<String, List<String>> getModules() {
        return Map.of("modules", modules);
    }

    private static void probeClass(String className, String moduleName, List<String> target) {
        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            target.add(moduleName);
        } catch (ClassNotFoundException ignored) {
        }
    }
}
