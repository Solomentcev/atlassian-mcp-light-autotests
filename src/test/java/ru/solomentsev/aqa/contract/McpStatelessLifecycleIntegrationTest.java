package ru.solomentsev.aqa.contract;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
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
@Epic("MCP Server")
    @Tag("lifecycle")
    @Tag("protocol")
class McpStatelessLifecycleIntegrationTest {

    private static final String PROTOCOL_VERSION = "2026-07-28";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-017 / MCP-019 / MCP-020 / MCP-021 / MCP-036 — Stateless и classic lifecycle до READY")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Stateless tools и classic lifecycle до READY")
    @Description("Предусловия: Запущен отдельный STDIO-процесс; classic initialize не отправлялся.\nШаги: вызвать classic tools/list и ping; выполнить stateless tools/list, tools/call и server/discover.\nОжидаемый результат: classic tools/list возвращает -32001; ping доступен до READY; stateless-запросы успешно обрабатываются без classic initialize и возвращают ожидаемые MCP-результаты.")
    @Tag("integration")
    @Tag("case-MCP-017")
    @Tag("case-MCP-019")
    @Tag("case-MCP-020")
    @Tag("case-MCP-021")
    @Tag("case-MCP-036")
    @Tag("case-MCP-010")
    void shouldListToolsAndDiscoverWithoutClassicInitialize() throws Exception {
        Allure.step("Stateless tools и classic lifecycle до READY", () -> {
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
                assertThat(pingBeforeReady.path("result").size()).isZero();

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
        
        });
    }
}
