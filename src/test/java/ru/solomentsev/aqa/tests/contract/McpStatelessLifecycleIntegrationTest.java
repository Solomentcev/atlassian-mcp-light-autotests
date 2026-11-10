package ru.solomentsev.aqa.tests.contract;

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
import ru.solomentsev.aqa.config.McpServerConfiguration;
import ru.solomentsev.aqa.mcp.McpProcessSession;
import ru.solomentsev.aqa.testdata.McpTestData;
import ru.solomentsev.aqa.testdata.ConfluenceTestData;

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

    private static final Duration REQUEST_TIMEOUT = McpTestData.REQUEST_TIMEOUT;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Feature("Classic Lifecycle")
    @Story("MCP-017 — Запрос до инициализации")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-017: Запрос до инициализации")
    @Description("Предусловия: STDIO-процесс запущен; initialize не отправлялся.\nШаги: вызвать classic tools/list.\nОжидаемый результат: сервер возвращает ошибку состояния протокола -32001 и не выполняет инструмент.")
    @Tag("case-MCP-017")
    void shouldRejectClassicToolsListBeforeInitialize() throws Exception {
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode response = sendRequest(session.client(), "tools/list", null, REQUEST_TIMEOUT);
            assertThat(response.path("error").path("code").asInt())
                    .as("До initialize сервер должен отклонять tools/list с ошибкой состояния -32001")
                    .isEqualTo(-32001);
        }
    
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-019 — Обнаружение возможностей stateless-режима")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-019: Обнаружение возможностей stateless-режима")
    @Description("Предусловия: STDIO-процесс запущен; classic initialize не отправлялся.\nШаги: вызвать server/discover с корректными stateless metadata.\nОжидаемый результат: сервер возвращает успешный результат обнаружения с supportedVersions, capabilities и serverInfo.")
    @Tag("case-MCP-019")
    void shouldDiscoverStatelessCapabilitiesWithoutInitialize() throws Exception {
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode response = Allure.step("Обнаружить stateless-возможности сервера", () -> Allure.step("Обнаружить stateless-возможности сервера", () -> session.client().discoverStateless(
                    McpTestData.STATELESS_PROTOCOL_VERSION, REQUEST_TIMEOUT)));
            assertThat(response.has("error"))
                    .as("Stateless MCP-запрос должен завершиться без JSON-RPC error")
                    .isFalse();
            assertThat(response.path("result").path("resultType").asText())
                    .as("Stateless-ответ должен иметь resultType=complete")
                    .isEqualTo("complete");
            assertThat(response.path("result").path("supportedVersions").isArray()).as("Проверка response.path('result').path('supportedVersions').isArray(): ожидалось isTrue").isTrue();
            assertThat(response.path("result").path("capabilities").path("tools").isObject()).as("Проверка response.path('result').path('capabilities').path('tools').isObject(): ожидалось isTrue").isTrue();
            assertThat(response.path("result").path("_meta")
                    .path("io.modelcontextprotocol/serverInfo").path("name").asText()).as("Проверка response.path('result').path('_meta') .path('io.modelcontextprotocol/serverInfo').path('name').asTex: ожидалось isEqualTo('atlassian-lite')")
                    .isEqualTo("atlassian-lite");
        }
    
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-020 — Вызов tools/list в stateless-режиме без initialize")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-020: Вызов tools/list в stateless-режиме без initialize")
    @Description("Предусловия: STDIO-процесс запущен; classic initialize не отправлялся.\nШаги: вызвать stateless tools/list с корректными metadata.\nОжидаемый результат: запрос успешно обработан без classic handshake; возвращены 13 инструментов и serverInfo metadata.")
    @Tag("case-MCP-020")
    void shouldListToolsStatelessWithoutInitialize() throws Exception {
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode response = Allure.step("Получить список инструментов в stateless-режиме", () -> Allure.step("Получить список инструментов в stateless-режиме", () -> session.client().listToolsStateless(
                    McpTestData.STATELESS_PROTOCOL_VERSION, REQUEST_TIMEOUT)));
            assertThat(response.has("error"))
                    .as("Stateless MCP-запрос должен завершиться без JSON-RPC error")
                    .isFalse();
            assertThat(response.path("result").path("tools").size())
                    .as("Stateless tools/list должен вернуть все 13 инструментов контракта")
                    .isEqualTo(13);
            assertThat(response.path("result").path("resultType").asText())
                    .as("Stateless-ответ должен иметь resultType=complete")
                    .isEqualTo("complete");
        }
    
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-021 — Вызов tools/call в stateless-режиме")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-021: Вызов tools/call в stateless-режиме")
    @Description("Предусловия: STDIO-процесс запущен; classic initialize не отправлялся; доступен безопасный read-инструмент.\nШаги: вызвать confluence_list_spaces через stateless tools/call с limit=1.\nОжидаемый результат: запрос выполняется без initialize и возвращает MCP tool result с resultType=complete.")
    @Tag("case-MCP-021")
    void shouldCallReadToolStatelessWithoutInitialize() throws Exception {
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            ObjectNode params = OBJECT_MAPPER.createObjectNode();
            params.put("name", ConfluenceTestData.LIST_SPACES_TOOL);
            params.putObject("arguments").put("limit", McpTestData.DEFAULT_LIMIT);
            JsonNode response = Allure.step("Вызвать read-инструмент в stateless-режиме", () -> session.client().requestStateless(
                    "tools/call",
                    params,
                    McpTestData.STATELESS_PROTOCOL_VERSION,
                    OBJECT_MAPPER.createObjectNode(),
                    null,
                    REQUEST_TIMEOUT
            ));
            assertThat(response.has("error"))
                    .as("Stateless MCP-запрос должен завершиться без JSON-RPC error")
                    .isFalse();
            assertThat(response.path("result").path("isError").asBoolean())
                    .as("Успешный read-инструмент не должен возвращать MCP tool error")
                    .isFalse();
            assertThat(response.path("result").path("resultType").asText())
                    .as("Stateless-ответ должен иметь resultType=complete")
                    .isEqualTo("complete");
        }
    
    }

    @Test
    @Feature("Classic Lifecycle")
    @Story("MCP-036 — Обработка ping до и после READY")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("MCP-036: Обработка ping до и после READY")
    @Description("Предусловия: STDIO-процесс запущен и находится в состоянии NEW.\nШаги: вызвать ping до initialize; выполнить initialize; вызвать ping до notifications/initialized; проверить недоступность tools/list; отправить notifications/initialized и повторить ping.\nОжидаемый результат: ping возвращает пустой result и не меняет состояние lifecycle; tools/list недоступен до notifications/initialized и доступен после перехода в READY.")
    @Tag("case-MCP-036")
    void shouldHandlePingWithoutChangingLifecycleState() throws Exception {
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode pingBeforeInitialize =
                    sendRequest(session.client(), "ping", null, REQUEST_TIMEOUT);
            assertEmptyPingResult(pingBeforeInitialize);

            ObjectNode initializeParams = OBJECT_MAPPER.createObjectNode();
            initializeParams.put("protocolVersion", McpTestData.CLASSIC_PROTOCOL_VERSION);
            initializeParams.putObject("capabilities");
            initializeParams.putObject("clientInfo")
                    .put("name", McpTestData.CLIENT_NAME)
                    .put("version", McpTestData.CLIENT_VERSION);
            JsonNode initialize = sendRequest(session.client(), 
                    "initialize", initializeParams, REQUEST_TIMEOUT);
            assertThat(initialize.has("error"))
                    .as("Корректный initialize должен завершиться без JSON-RPC error")
                    .isFalse();

            JsonNode pingBeforeReady =
                    sendRequest(session.client(), "ping", null, REQUEST_TIMEOUT);
            assertEmptyPingResult(pingBeforeReady);

            JsonNode toolsBeforeReady =
                    sendRequest(session.client(), "tools/list", null, REQUEST_TIMEOUT);
            assertThat(toolsBeforeReady.path("error").path("code").asInt())
                    .as("До notifications/initialized инструменты должны оставаться недоступными")
                    .isEqualTo(-32001);

            Allure.step("Отправить notifications/initialized", () -> session.client().notification("notifications/initialized", null));

            JsonNode pingAfterReady =
                    sendRequest(session.client(), "ping", null, REQUEST_TIMEOUT);
            assertEmptyPingResult(pingAfterReady);

            JsonNode toolsAfterReady =
                    sendRequest(session.client(), "tools/list", null, REQUEST_TIMEOUT);
            assertThat(toolsAfterReady.has("error"))
                    .as("После notifications/initialized tools/list должен выполняться успешно")
                    .isFalse();
            assertThat(toolsAfterReady.path("result").path("tools").isArray())
                    .as("После перехода в READY сервер должен вернуть массив инструментов")
                    .isTrue();
        }
    
    }

    private static JsonNode sendRequest(ru.solomentsev.aqa.mcp.McpClient client,
                                        String method,
                                        JsonNode params,
                                        Duration timeout) {
        return Allure.step("Отправить MCP-запрос " + method,
                () -> client.request(method, params, timeout));
    }

    private static void assertEmptyPingResult(JsonNode response) {
        assertThat(response.has("error"))
                        .as("Stateless MCP-запрос должен завершиться без JSON-RPC error")
                        .isFalse();
        assertThat(response.path("result").isObject())
                .as("Ping должен возвращать объект result")
                .isTrue();
        assertThat(response.path("result").isEmpty())
                .as("Результат ping должен быть пустым объектом")
                .isTrue();
    }
}
