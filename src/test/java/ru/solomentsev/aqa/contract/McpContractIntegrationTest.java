package ru.solomentsev.aqa.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.solomentsev.aqa.junit.McpServerTest;
import ru.solomentsev.aqa.mcp.McpClient;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет публичный stateless MCP contract работающего тестового сервера.
 *
 * <p>Запускается отдельно с Maven-профилем {@code integration} и требует
 * настроенного тестового экземпляра Atlassian MCP Light.</p>
 */
@McpServerTest
@Tag("integration")
@EnabledIfSystemProperty(named = "AQA_RUN_INTEGRATION", matches = "true")
class McpContractIntegrationTest {

    private static final String STATELESS_PROTOCOL_VERSION = "2026-07-28";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> EXPECTED_TOOLS = Set.of(
            "jira_get_issue",
            "jira_search",
            "jira_create_issue",
            "jira_transition",
            "jira_add_comment",
            "jira_get_project",
            "jira_get_user",
            "confluence_get_page",
            "confluence_list_spaces",
            "confluence_get_space",
            "confluence_search",
            "confluence_create_page",
            "confluence_update_page"
    );

    @Test
    void shouldExposeSupportedStatelessProtocol(McpClient client) {
        JsonNode response = client.discoverStateless(
                STATELESS_PROTOCOL_VERSION,
                REQUEST_TIMEOUT
        );

        assertThat(response.has("error")).isFalse();
        JsonNode result = response.path("result");
        assertThat(result.path("resultType").asText()).isEqualTo("complete");
        assertThat(result.path("supportedVersions").findValuesAsText(""))
                .isNotEmpty();
        assertThat(result.path("supportedVersions").toString())
                .contains("2026-07-28", "2025-11-25", "2024-11-05");
        assertThat(result.path("capabilities").path("tools").isObject()).isTrue();
        assertThat(result.path("_meta").path("io.modelcontextprotocol/serverInfo")
                .path("name").asText()).isEqualTo("atlassian-lite");
        assertThat(result.path("ttlMs").asLong()).isEqualTo(300_000L);
        assertThat(result.path("cacheScope").asText()).isEqualTo("public");
    }

    @Test
    void shouldExposeAllThirteenToolsAndValidInputSchemas(McpClient client) {
        JsonNode response = client.listToolsStateless(
                STATELESS_PROTOCOL_VERSION,
                REQUEST_TIMEOUT
        );

        assertThat(response.has("error")).isFalse();
        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools).hasSize(13);

        Set<String> actualNames = new HashSet<>();
        for (JsonNode tool : tools) {
            actualNames.add(tool.path("name").asText());
            assertThat(tool.path("description").isTextual()).isTrue();
            JsonNode schema = tool.path("inputSchema");
            assertThat(schema.path("type").asText()).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            assertThat(schema.path("properties").isObject()).isTrue();
            assertThat(schema.path("required").isArray()).isTrue();
        }

        assertThat(actualNames).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS);
        assertThat(response.path("result").path("resultType").asText())
                .isEqualTo("complete");
    }

    @Test
    void shouldReturnToolErrorForUnknownToolWithoutJsonRpcError(McpClient client) {
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", "aqa_unknown_tool");
        params.set("arguments", OBJECT_MAPPER.createObjectNode());

        JsonNode response = client.requestStateless(
                "tools/call",
                params,
                STATELESS_PROTOCOL_VERSION,
                OBJECT_MAPPER.createObjectNode(),
                null,
                REQUEST_TIMEOUT
        );

        assertThat(response.has("error")).isFalse();
        JsonNode result = response.path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").isArray()).isTrue();
        assertThat(result.path("content").get(0).path("text").asText())
                .startsWith("Ошибка: ");
        assertThat(result.path("resultType").asText()).isEqualTo("complete");
    }
}
