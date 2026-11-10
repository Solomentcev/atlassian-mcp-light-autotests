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
 * Проверяет отдельные сценарии stateless и classic MCP lifecycle.
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
    @Feature("Classic Lifecycle")
    @Story("MCP-017 — Запрос до инициализации")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-017: Запрос до инициализации")
    @Description("Предусловия: STDIO-процесс запущен; initialize не отправлялся.\nШаги: вызвать classic tools/list.\nОжидаемый результат: сервер возвращает ошибку состояния протокола -32001 и не выполняет инструмент.")
    @Tag("case-MCP-017")
    void shouldRejectClassicToolsListBeforeInitialize() throws Exception {
        Allure.step("Отклонение classic tools/list до initialize", () -> {
            try (McpProcessSession session =
                         McpServerConfiguration.fromEnvironment().startStatelessSession()) {
                JsonNode response = session.client().request("tools/list", null, REQUEST_TIMEOUT);
                assertThat(response.path("error").path("code").asInt()).isEqualTo(-32001);
            }
        });
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-019 — Обнаружение возможностей stateless-режима")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-019: Обнаружение возможностей stateless-режима")
    @Description("Предусловия: STDIO-процесс запущен; classic initialize не отправлялся.\nШаги: вызвать server/discover с корректными stateless metadata.\nОжидаемый результат: сервер возвращает успешный результат обнаружения с supportedVersions, capabilities и serverInfo.")
    @Tag("case-MCP-019")
    void shouldDiscoverStatelessCapabilitiesWithoutInitialize() throws Exception {
        Allure.step("Обнаружение stateless capabilities без initialize", () -> {
            try (McpProcessSession session =
                         McpServerConfiguration.fromEnvironment().startStatelessSession()) {
                JsonNode response = session.client().discoverStateless(
                        PROTOCOL_VERSION, REQUEST_TIMEOUT);
                assertThat(response.has("error")).isFalse();
                assertThat(response.path("result").path("resultType").asText()).isEqualTo("complete");
                assertThat(response.path("result").path("supportedVersions").isArray()).isTrue();
                assertThat(response.path("result").path("capabilities").path("tools").isObject()).isTrue();
                assertThat(response.path("result").path("_meta")
                        .path("io.modelcontextprotocol/serverInfo").path("name").asText())
                        .isEqualTo("atlassian-lite");
            }
        });
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-020 — Вызов tools/list в stateless-режиме без initialize")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-020: Вызов tools/list в stateless-режиме без initialize")
    @Description("Предусловия: STDIO-процесс запущен; classic initialize не отправлялся.\nШаги: вызвать stateless tools/list с корректными metadata.\nОжидаемый результат: запрос успешно обработан без classic handshake; возвращены 13 инструментов и serverInfo metadata.")
    @Tag("case-MCP-020")
    void shouldListToolsStatelessWithoutInitialize() throws Exception {
        Allure.step("Получение tools/list без classic initialize", () -> {
            try (McpProcessSession session =
                         McpServerConfiguration.fromEnvironment().startStatelessSession()) {
                JsonNode response = session.client().listToolsStateless(
                        PROTOCOL_VERSION, REQUEST_TIMEOUT);
                assertThat(response.has("error")).isFalse();
                assertThat(response.path("result").path("tools").size()).isEqualTo(13);
                assertThat(response.path("result").path("resultType").asText()).isEqualTo("complete");
            }
        });
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-021 — Вызов tools/call в stateless-режиме")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-021: Вызов tools/call в stateless-режиме")
    @Description("Предусловия: STDIO-процесс запущен; classic initialize не отправлялся; доступен безопасный read-инструмент.\nШаги: вызвать confluence_list_spaces через stateless tools/call с limit=1.\nОжидаемый результат: запрос выполняется без initialize и возвращает MCP tool result с resultType=complete.")
    @Tag("case-MCP-021")
    void shouldCallReadToolStatelessWithoutInitialize() throws Exception {
        Allure.step("Вызов read-инструмента без classic initialize", () -> {
            try (McpProcessSession session =
                         McpServerConfiguration.fromEnvironment().startStatelessSession()) {
                ObjectNode params = OBJECT_MAPPER.createObjectNode();
                params.put("name", "confluence_list_spaces");
                params.putObject("arguments").put("limit", 1);
                JsonNode response = session.client().requestStateless(
                        "tools/call",
                        params,
                        PROTOCOL_VERSION,
                        OBJECT_MAPPER.createObjectNode(),
                        null,
                        REQUEST_TIMEOUT
                );
                assertThat(response.has("error")).isFalse();
                assertThat(response.path("result").path("isError").asBoolean()).isFalse();
                assertThat(response.path("result").path("resultType").asText()).isEqualTo("complete");
            }
        });
    }

    @Test
    @Feature("Classic Lifecycle")
    @Story("MCP-036 — Обработка ping до и после READY")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("MCP-036: Обработка ping до и после READY")
    @Description("Предусловия: STDIO-процесс запущен и находится в состоянии NEW.\nШаги: вызвать ping до initialize; выполнить initialize; вызвать ping до notifications/initialized; проверить недоступность tools/list; отправить notifications/initialized и повторить ping.\nОжидаемый результат: ping возвращает пустой result и не меняет состояние lifecycle; tools/list недоступен до notifications/initialized и доступен после перехода в READY.")
    @Tag("case-MCP-036")
    void shouldHandlePingWithoutChangingLifecycleState() throws Exception {
        Allure.step("Проверка ping на разных этапах classic lifecycle", () -> {
            try (McpProcessSession session =
                         McpServerConfiguration.fromEnvironment().startStatelessSession()) {
                JsonNode pingBeforeInitialize =
                        session.client().request("ping", null, REQUEST_TIMEOUT);
                assertEmptyPingResult(pingBeforeInitialize);

                ObjectNode initializeParams = OBJECT_MAPPER.createObjectNode();
                initializeParams.put("protocolVersion", "2025-11-25");
                initializeParams.putObject("capabilities");
                initializeParams.putObject("clientInfo")
                        .put("name", "atlassian-mcp-light-autotests")
                        .put("version", "1.0");
                JsonNode initialize = session.client().request(
                        "initialize", initializeParams, REQUEST_TIMEOUT);
                assertThat(initialize.has("error")).isFalse();

                JsonNode pingBeforeReady =
                        session.client().request("ping", null, REQUEST_TIMEOUT);
                assertEmptyPingResult(pingBeforeReady);

                JsonNode toolsBeforeReady =
                        session.client().request("tools/list", null, REQUEST_TIMEOUT);
                assertThat(toolsBeforeReady.path("error").path("code").asInt()).isEqualTo(-32001);

                session.client().notification("notifications/initialized", null);

                JsonNode pingAfterReady =
                        session.client().request("ping", null, REQUEST_TIMEOUT);
                assertEmptyPingResult(pingAfterReady);

                JsonNode toolsAfterReady =
                        session.client().request("tools/list", null, REQUEST_TIMEOUT);
                assertThat(toolsAfterReady.has("error")).isFalse();
                assertThat(toolsAfterReady.path("result").path("tools").isArray()).isTrue();
            }
        });
    }

    private static void assertEmptyPingResult(JsonNode response) {
        assertThat(response.has("error")).isFalse();
        assertThat(response.path("result").isObject()).isTrue();
        assertThat(response.path("result").isEmpty()).isTrue();
    }
}
