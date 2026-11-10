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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private static final Map<String, ToolContract> EXPECTED_TOOLS = expectedTools();

    @Test
    void shouldExposeSupportedStatelessProtocol(McpClient client) {
        JsonNode response = client.discoverStateless(
                STATELESS_PROTOCOL_VERSION,
                REQUEST_TIMEOUT
        );

        assertSuccessfulJsonRpc(response);
        JsonNode result = response.path("result");
        assertThat(result.path("resultType").asText()).isEqualTo("complete");
        assertThat(textValues(result.path("supportedVersions")))
                .containsExactly("2026-07-28", "2025-11-25", "2024-11-05");
        assertThat(result.path("capabilities").path("tools").isObject()).isTrue();
        assertThat(result.path("_meta").path("io.modelcontextprotocol/serverInfo")
                .path("name").asText()).isEqualTo("atlassian-lite");
        assertThat(result.path("_meta").path("io.modelcontextprotocol/serverInfo")
                .path("version").asText()).isEqualTo("1.0.0");
        assertThat(result.path("ttlMs").asLong()).isEqualTo(300_000L);
        assertThat(result.path("cacheScope").asText()).isEqualTo("public");
    }

    @Test
    void shouldExposeAllThirteenToolsWithExactSchemasAndAnnotations(McpClient client) {
        JsonNode response = client.listToolsStateless(
                STATELESS_PROTOCOL_VERSION,
                REQUEST_TIMEOUT
        );

        assertSuccessfulJsonRpc(response);
        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(EXPECTED_TOOLS.size());

        Set<String> actualNames = new HashSet<>();
        for (JsonNode tool : tools) {
            String name = tool.path("name").asText();
            actualNames.add(name);
            ToolContract expected = EXPECTED_TOOLS.get(name);
            assertThat(expected)
                    .as("Unexpected tool returned by tools/list: %s", name)
                    .isNotNull();

            assertThat(tool.path("description").isTextual())
                    .as("description of %s", name).isTrue();

            JsonNode schema = tool.path("inputSchema");
            assertThat(schema.path("type").asText()).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            assertThat(fieldNames(schema.path("properties")))
                    .containsExactlyInAnyOrderElementsOf(expected.propertyTypes().keySet());
            assertThat(textValues(schema.path("required")))
                    .containsExactlyInAnyOrderElementsOf(expected.required());

            for (Map.Entry<String, String> property : expected.propertyTypes().entrySet()) {
                JsonNode definition = schema.path("properties").path(property.getKey());
                assertThat(definition.path("type").asText())
                        .as("%s.%s type", name, property.getKey())
                        .isEqualTo(property.getValue());
                if ("string".equals(property.getValue())) {
                    assertThat(definition.path("minLength").asInt())
                            .as("%s.%s minLength", name, property.getKey())
                            .isEqualTo(1);
                }
                if ("integer".equals(property.getValue())) {
                    assertThat(definition.path("minimum").asInt())
                            .as("%s.%s minimum", name, property.getKey())
                            .isEqualTo(1);
                    assertThat(definition.path("maximum").asInt())
                            .as("%s.%s maximum", name, property.getKey())
                            .isEqualTo(100);
                }
            }

            assertAnnotations(tool.path("annotations"), expected);
        }

        assertThat(actualNames).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS.keySet());
        assertThat(response.path("result").path("resultType").asText())
                .isEqualTo("complete");
        assertThat(response.path("result").path("_meta")
                .path("io.modelcontextprotocol/serverInfo").path("name").asText())
                .isEqualTo("atlassian-lite");
    }

    @Test
    void shouldRejectUnsupportedStatelessProtocolVersion(McpClient client) {
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        ObjectNode metadata = params.putObject("_meta");
        metadata.put("io.modelcontextprotocol/protocolVersion", "2099-01-01");
        metadata.putObject("io.modelcontextprotocol/clientCapabilities");

        JsonNode response = client.request(
                "server/discover",
                params,
                REQUEST_TIMEOUT
        );

        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32022);
        assertThat(textValues(response.path("error").path("data").path("supportedVersions")))
                .containsExactly("2026-07-28");
    }

    @Test
    void shouldReturnToolErrorForUnknownToolWithoutJsonRpcError(McpClient client) {
        JsonNode response = callTool(client, "aqa_unknown_tool", OBJECT_MAPPER.createObjectNode());

        assertToolError(response);
    }

    @Test
    void shouldRejectMissingRequiredToolArgument(McpClient client) {
        JsonNode response = callTool(client, "jira_get_issue", OBJECT_MAPPER.createObjectNode());

        assertToolError(response);
    }

    @Test
    void shouldRejectUnknownToolArgument(McpClient client) {
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("issue_key", "AQA-1");
        arguments.put("unexpected", "value");

        assertToolError(callTool(client, "jira_get_issue", arguments));
    }

    @Test
    void shouldRejectBlankStringToolArgument(McpClient client) {
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("issue_key", "   ");

        assertToolError(callTool(client, "jira_get_issue", arguments));
    }

    @Test
    void shouldRejectLimitOutsidePublishedBounds(McpClient client) {
        ObjectNode zeroLimit = OBJECT_MAPPER.createObjectNode();
        zeroLimit.put("jql", "project = AQA");
        zeroLimit.put("limit", 0);
        assertToolError(callTool(client, "jira_search", zeroLimit));

        ObjectNode excessiveLimit = OBJECT_MAPPER.createObjectNode();
        excessiveLimit.put("jql", "project = AQA");
        excessiveLimit.put("limit", 101);
        assertToolError(callTool(client, "jira_search", excessiveLimit));
    }

    @Test
    void shouldRequireTitleOrContentForConfluenceUpdate(McpClient client) {
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("page_id", "1");

        assertToolError(callTool(client, "confluence_update_page", arguments));
    }

    private static JsonNode callTool(McpClient client, String name, ObjectNode arguments) {
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments);
        return client.requestStateless(
                "tools/call",
                params,
                STATELESS_PROTOCOL_VERSION,
                OBJECT_MAPPER.createObjectNode(),
                null,
                REQUEST_TIMEOUT
        );
    }

    private static void assertSuccessfulJsonRpc(JsonNode response) {
        assertThat(response.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.has("id")).isTrue();
        assertThat(response.has("error")).isFalse();
        assertThat(response.path("result").isObject()).isTrue();
    }

    private static void assertToolError(JsonNode response) {
        assertSuccessfulJsonRpc(response);
        JsonNode result = response.path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").isArray()).isTrue();
        assertThat(result.path("content").size()).isGreaterThan(0);
        assertThat(result.path("content").get(0).path("type").asText()).isEqualTo("text");
        assertThat(result.path("content").get(0).path("text").asText())
                .startsWith("Ошибка: ");
        assertThat(result.has("error")).isFalse();
    }

    private static void assertAnnotations(JsonNode annotations, ToolContract expected) {
        assertThat(annotations.path("readOnlyHint").isBoolean()).isTrue();
        assertThat(annotations.path("destructiveHint").isBoolean()).isTrue();
        assertThat(annotations.path("idempotentHint").isBoolean()).isTrue();
        assertThat(annotations.path("openWorldHint").isBoolean()).isTrue();
        assertThat(annotations.path("readOnlyHint").asBoolean())
                .isEqualTo(expected.readOnly());
        assertThat(annotations.path("destructiveHint").asBoolean())
                .isEqualTo(expected.destructive());
        assertThat(annotations.path("idempotentHint").asBoolean())
                .isEqualTo(expected.idempotent());
        assertThat(annotations.path("openWorldHint").asBoolean()).isTrue();
    }

    private static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(value -> values.add(value.asText()));
        }
        return values;
    }

    private static Set<String> fieldNames(JsonNode object) {
        Set<String> names = new HashSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Map<String, ToolContract> expectedTools() {
        Map<String, ToolContract> tools = new HashMap<>();
        add(tools, "jira_get_issue", fields("issue_key", "string"), set("issue_key"), true, false, true);
        add(tools, "jira_search", fields("jql", "string", "limit", "integer"), set("jql"), true, false, true);
        add(tools, "jira_create_issue",
                fields("project_key", "string", "summary", "string", "description", "string", "issue_type", "string"),
                set("project_key", "summary", "description"), false, false, false);
        add(tools, "jira_transition",
                fields("issue_key", "string", "transition_name", "string"),
                set("issue_key", "transition_name"), false, true, false);
        add(tools, "jira_add_comment",
                fields("issue_key", "string", "comment", "string"),
                set("issue_key", "comment"), false, false, false);
        add(tools, "jira_get_project", fields("project_key", "string"), set("project_key"), true, false, true);
        add(tools, "jira_get_user", fields("user_identifier", "string"), set("user_identifier"), true, false, true);
        add(tools, "confluence_get_page", fields("page_id", "string"), set("page_id"), true, false, true);
        add(tools, "confluence_list_spaces", fields("limit", "integer"), set(), true, false, true);
        add(tools, "confluence_get_space", fields("space_key", "string"), set("space_key"), true, false, true);
        add(tools, "confluence_search", fields("cql", "string", "limit", "integer"), set("cql"), true, false, true);
        add(tools, "confluence_create_page",
                fields("space_key", "string", "title", "string", "content", "string", "parent_id", "string"),
                set("space_key", "title", "content"), false, false, false);
        add(tools, "confluence_update_page",
                fields("page_id", "string", "version", "integer", "title", "string", "content", "string"),
                set("page_id"), false, true, false);
        return Map.copyOf(tools);
    }

    private static void add(Map<String, ToolContract> tools,
                            String name,
                            Map<String, String> properties,
                            Set<String> required,
                            boolean readOnly,
                            boolean destructive,
                            boolean idempotent) {
        tools.put(name, new ToolContract(properties, required, readOnly, destructive, idempotent));
    }

    private static Map<String, String> fields(String... values) {
        Map<String, String> fields = new HashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            fields.put(values[index], values[index + 1]);
        }
        return Map.copyOf(fields);
    }

    private static Set<String> set(String... values) {
        return Set.of(values);
    }

    private record ToolContract(Map<String, String> propertyTypes,
                                Set<String> required,
                                boolean readOnly,
                                boolean destructive,
                                boolean idempotent) {
    }
}
