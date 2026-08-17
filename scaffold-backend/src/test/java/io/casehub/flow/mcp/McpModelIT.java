package io.casehub.flow.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.mcp.CaseHubMcpTools;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class McpModelIT {

    @Inject CaseHubMcpTools mcpTools;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void tier0ListsAllFourDomains() throws Exception {
        String json = mcpTools.casehub_model(null);
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        List<Map<String, Object>> domains =
                (List<Map<String, Object>>) result.get("domains");

        List<String> domainNames = domains.stream()
                .map(d -> (String) d.get("name"))
                .toList();

        assertThat(domainNames).contains("engine", "work", "ledger", "qhorus");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier0IncludesEnricherSummaries() throws Exception {
        String json = mcpTools.casehub_model(null);
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        List<Map<String, Object>> domains =
                (List<Map<String, Object>>) result.get("domains");

        for (String name : List.of("engine", "work", "ledger", "qhorus")) {
            Map<String, Object> domain = domains.stream()
                    .filter(d -> name.equals(d.get("name")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing domain: " + name));

            assertThat(domain.get("summary"))
                    .as("Summary for domain '%s'", name)
                    .isNotNull();
            assertThat((String) domain.get("summary"))
                    .as("Summary for domain '%s' is non-empty", name)
                    .isNotBlank();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier1EngineHasExpectedOperations() throws Exception {
        String json = mcpTools.casehub_model("engine");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        assertThat(result).containsEntry("domain", "engine");

        List<Map<String, Object>> queries =
                (List<Map<String, Object>>) result.get("queries");
        List<String> queryNames = queries.stream()
                .map(q -> (String) q.get("name")).toList();
        assertThat(queryNames).contains("cases", "caseById");

        List<Map<String, Object>> mutations =
                (List<Map<String, Object>>) result.get("mutations");
        List<String> mutationNames = mutations.stream()
                .map(m -> (String) m.get("name")).toList();
        assertThat(mutationNames).contains("startCase", "cancelCase");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tier1QhorusHasExpectedOperations() throws Exception {
        String json = mcpTools.casehub_model("qhorus");
        Map<String, Object> result = mapper.readValue(json, new TypeReference<>() {});

        assertThat(result).containsEntry("domain", "qhorus");

        List<Map<String, Object>> queries =
                (List<Map<String, Object>>) result.get("queries");
        List<String> queryNames = queries.stream()
                .map(q -> (String) q.get("name")).toList();
        assertThat(queryNames).contains("channels", "channel", "channelMessages", "commitments");

        List<Map<String, Object>> mutations =
                (List<Map<String, Object>>) result.get("mutations");
        List<String> mutationNames = mutations.stream()
                .map(m -> (String) m.get("name")).toList();
        assertThat(mutationNames).contains("createChannel", "deleteChannel",
                "pauseChannel", "resumeChannel", "dispatchMessage");
    }

    @Test
    void casehubActionDispatchesToEngine() throws Exception {
        String json = mcpTools.casehub_action(
                "engine", "caseDefinitions",
                "{\"page\": {\"offset\": 0, \"limit\": 10}}");
        assertThat(json).isNotNull();
        assertThat(json).contains("items");
    }

    @Test
    void casehubActionRejectsUnknownDomain() {
        try {
            mcpTools.casehub_action("nonexistent", "someOp", "{}");
            assertThat(true).as("Should have thrown").isFalse();
        } catch (Exception e) {
            assertThat(e.getMessage()).containsIgnoringCase("unknown");
        }
    }
}
