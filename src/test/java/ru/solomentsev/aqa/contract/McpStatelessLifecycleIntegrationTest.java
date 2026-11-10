package ru.solomentsev.aqa.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.solomentsev.aqa.junit.McpServerConfiguration;
import ru.solomentsev.aqa.mcp.McpProcessSession;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет stateless MCP lifecycle на отдельном процессе без classic initialize.
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "AQA_RUN_INTEGRATION", matches = "true")
class McpStatelessLifecycleIntegrationTest {

    private static final String PROTOCOL_VERSION = "2026-07-28";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldListToolsAndDiscoverWithoutClassicInitialize() throws Exception {
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode classicToolsBeforeReady = session.client().request(
                    "tools/list",
                    null,
                    REQUEST_TIMEOUT
            );
            assertThat(classicToolsBeforeReady.path("error").path("code").asInt())
                    .isEqualTo(-32001);

            JsonNode pingBeforeReady = session.client().request("ping", null, REQUEST_TIMEOUT);
            assertThat(pingBeforeReady.path("result").isObject()).isTrue();
            assertThat(pingBeforeReady.path("result").isEmpty()).isTrue();

            JsonNode tools = session.client().listToolsStateless(
                    PROTOCOL_VERSION,
                    REQUEST_TIMEOUT
            );

            assertThat(tools.path("jsonrpc").asText()).isEqualTo("2.0");
            assertThat(tools.has("error")).isFalse();
            assertThat(tools.path("result").path("tools").size()).isEqualTo(13);

            ObjectNode params = OBJECT_MAPPER.createObjectNode();
            params.put("name", "aqa_unknown_tool");
            params.set("arguments", OBJECT_MAPPER.createObjectNode());
            JsonNode statelessToolCall = session.client().requestStateless(
                    "tools/call",
                    params,
                    PROTOCOL_VERSION,
                    OBJECT_MAPPER.createObjectNode(),
                    null,
                    REQUEST_TIMEOUT
            );
            assertThat(statelessToolCall.has("error")).isFalse();
            assertThat(statelessToolCall.path("result").path("isError").asBoolean()).isTrue();
            assertThat(statelessToolCall.path("result").path("resultType").asText())
                    .isEqualTo("complete");

            JsonNode discovery = session.client().discoverStateless(
                    PROTOCOL_VERSION,
                    REQUEST_TIMEOUT
            );

            assertThat(discovery.path("jsonrpc").asText()).isEqualTo("2.0");
            assertThat(discovery.has("error")).isFalse();
            assertThat(discovery.path("result").path("resultType").asText())
                    .isEqualTo("complete");
            assertThat(discovery.path("result").path("_meta")
                    .path("io.modelcontextprotocol/serverInfo")
                    .path("name").asText()).isEqualTo("atlassian-lite");

        }
    }
}
