package ru.solomentsev.aqa.contract;

import com.fasterxml.jackson.databind.JsonNode;
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

    @Test
    void shouldListToolsAndDiscoverWithoutClassicInitialize() throws Exception {
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode tools = session.client().listToolsStateless(
                    PROTOCOL_VERSION,
                    REQUEST_TIMEOUT
            );

            assertThat(tools.path("jsonrpc").asText()).isEqualTo("2.0");
            assertThat(tools.has("error")).isFalse();
            assertThat(tools.path("result").path("tools").size()).isEqualTo(13);

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
