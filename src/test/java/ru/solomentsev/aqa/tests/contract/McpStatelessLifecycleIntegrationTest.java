package ru.solomentsev.aqa.tests.contract;

import io.qameta.allure.Allure;
import io.qameta.allure.Step;
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

import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThat;

/**
 * Проверяет отдельные сценарии stateless и classic MCP lifecycle.
 */
@Tag("integration")
@EnabledIfSystemProperty(named = "AQA_RUN_INTEGRATION", matches = "true")
@Epic("MCP Server")
@Tag("lifecycle")
@Tag("protocol")
@Tag("regression")
class McpStatelessLifecycleIntegrationTest {

    private static final Duration REQUEST_TIMEOUT = McpTestData.REQUEST_TIMEOUT;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Feature("Classic Lifecycle")
    @Story("MCP-017 — Запрос до инициализации")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-017: classic tools/list в состоянии NEW до initialize возвращает error=" + McpTestData.MCP_SERVER_NOT_INITIALIZED + ")")
    @Description("Предусловия: запущен новый MCP STDIO-процесс; initialize и notifications/initialized не отправлялись.\nШаги: отправить classic JSON-RPC request method=tools/list без stateless metadata.\nОжидаемый результат: сервер возвращает JSON-RPC error code=" + McpTestData.MCP_SERVER_NOT_INITIALIZED + " (сервер ещё не инициализирован); инструменты не перечисляются.")
    @Tag("case-MCP-017")
    void shouldRejectClassicToolsListBeforeInitialize() throws Exception {
        Allure.parameter("MCP method", McpTestData.TOOLS_LIST_METHOD);
        Allure.parameter("Lifecycle state", "NEW; initialize не выполнялся");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.MCP_SERVER_NOT_INITIALIZED);
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode response = sendRequest(session.client(), McpTestData.TOOLS_LIST_METHOD, null, REQUEST_TIMEOUT);
            assertThat(response.path("error").path("code").asInt())
                    .as("До initialize сервер должен отклонять tools/list с ошибкой состояния " + McpTestData.MCP_SERVER_NOT_INITIALIZED)
                    .isEqualTo(McpTestData.MCP_SERVER_NOT_INITIALIZED);
        }
    
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-019 — Обнаружение возможностей stateless-режима")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-019: server/discover без initialize [protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + "] возвращает capabilities и serverInfo")
    @Description("Предусловия: запущен новый MCP STDIO-процесс; classic initialize не выполнялся.\nШаги: отправить server/discover с protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + " и clientCapabilities; проверить supportedVersions, capabilities.tools, ttlMs, cacheScope и serverInfo.\nОжидаемый результат: JSON-RPC error отсутствует; resultType=complete; supportedVersions содержит три версии в ожидаемом порядке; tools capability объявлена; TTL=" + McpTestData.DISCOVERY_TTL_MS + " ms; cacheScope=" + McpTestData.DISCOVERY_CACHE_SCOPE + "; serverInfo совпадает с метаданными сервера.")
    @Tag("case-MCP-019")
    @Tag("smoke")
    void shouldDiscoverStatelessCapabilitiesWithoutInitialize() throws Exception {
        Allure.parameter("MCP method", "server/discover");
        Allure.parameter("MCP protocolVersion", McpTestData.STATELESS_PROTOCOL_VERSION);

        McpProcessSession session = Allure.step(
                "Запустить новый MCP STDIO-процесс без classic initialize",
                () -> McpServerConfiguration.fromEnvironment().startStatelessSession());
        try (session) {
            JsonNode response = Allure.step(
                    "Отправить server/discover: protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION,
                    () -> session.client().discoverStateless(
                            McpTestData.STATELESS_PROTOCOL_VERSION, REQUEST_TIMEOUT));

            JsonNode result = response.path("result");
            assertFalseStep("JSON-RPC response не содержит error", response.has("error"));
            assertEqualsStep("result.resultType", result.path("resultType").asText(), "complete");

            JsonNode supportedVersions = result.path("supportedVersions");
            assertTrueStep("result.supportedVersions является массивом", supportedVersions.isArray());
            assertEqualsStep("Количество поддерживаемых версий", supportedVersions.size(), 3);
            assertEqualsStep("supportedVersions[0] — stateless protocolVersion",
                    supportedVersions.get(0).asText(), McpTestData.STATELESS_PROTOCOL_VERSION);
            assertEqualsStep("supportedVersions[1] — classic protocolVersion",
                    supportedVersions.get(1).asText(), McpTestData.CLASSIC_PROTOCOL_VERSION);
            assertEqualsStep("supportedVersions[2] — legacy protocolVersion",
                    supportedVersions.get(2).asText(), McpTestData.LEGACY_PROTOCOL_VERSION);

            boolean toolsCapabilityIsObject = result.path("capabilities").path("tools").isObject();
            assertTrueStep("result.capabilities.tools является объектом", toolsCapabilityIsObject);
            assertEqualsStep("result.ttlMs", result.path("ttlMs").asLong(), McpTestData.DISCOVERY_TTL_MS);
            assertEqualsStep("result.cacheScope", result.path("cacheScope").asText(),
                    McpTestData.DISCOVERY_CACHE_SCOPE);

            JsonNode serverInfo = result.path("_meta").path("io.modelcontextprotocol/serverInfo");
            assertEqualsStep("serverInfo.name", serverInfo.path("name").asText(), McpTestData.SERVER_NAME);
            assertEqualsStep("serverInfo.version", serverInfo.path("version").asText(),
                    McpTestData.SERVER_VERSION);
        }
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-020 — Вызов tools/list в stateless-режиме без initialize")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-020: stateless tools/list без initialize [protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + "] возвращает " + McpTestData.EXPECTED_TOOL_COUNT + " tools")
    @Description("Предусловия: запущен новый MCP STDIO-процесс; classic initialize не выполнялся.\nШаги: отправить stateless tools/list с protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + " и clientCapabilities; проверить result.tools, resultType и serverInfo.\nОжидаемый результат: JSON-RPC error отсутствует; result.tools содержит ровно " + McpTestData.EXPECTED_TOOL_COUNT + " инструментов; resultType=complete; serverInfo.name=" + McpTestData.SERVER_NAME + " и serverInfo.version=" + McpTestData.SERVER_VERSION + ".")
    @Tag("case-MCP-020")
    @Tag("smoke")
    void shouldListToolsStatelessWithoutInitialize() throws Exception {
        Allure.parameter("MCP method", McpTestData.TOOLS_LIST_METHOD);
        Allure.parameter("MCP protocolVersion", McpTestData.STATELESS_PROTOCOL_VERSION);
        Allure.parameter("Classic initialize", "не выполняется");
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode response = Allure.step("Выполнить tools/list в stateless-режиме: protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION, () -> session.client().listToolsStateless(
                    McpTestData.STATELESS_PROTOCOL_VERSION, REQUEST_TIMEOUT));
            assertThat(response.has("error"))
                    .as("Stateless MCP-запрос должен завершиться без JSON-RPC error")
                    .isFalse();
            assertThat(response.path("result").path("tools").size())
                    .as("Stateless tools/list должен вернуть все 13 инструментов контракта")
                    .isEqualTo(McpTestData.EXPECTED_TOOL_COUNT);
            assertThat(response.path("result").path("resultType").asText())
                    .as("Stateless-ответ должен иметь resultType=complete")
                    .isEqualTo("complete");
            assertThat(response.path("result").path("_meta")
                    .path("io.modelcontextprotocol/serverInfo").path("name").asText())
                    .as("Stateless tools/list должен возвращать serverInfo с именем сервера")
                    .isEqualTo(McpTestData.SERVER_NAME);
            assertThat(response.path("result").path("_meta")
                    .path("io.modelcontextprotocol/serverInfo").path("version").asText())
                    .as("Stateless tools/list должен возвращать версию MCP-сервера в serverInfo")
                    .isEqualTo(McpTestData.SERVER_VERSION);
        }
    
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-021 — Вызов tools/call в stateless-режиме")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-021: stateless tools/call " + ConfluenceTestData.LIST_SPACES_TOOL + " [limit=" + McpTestData.DEFAULT_LIMIT + ", protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + "] без initialize")
    @Description("Предусловия: запущен новый MCP STDIO-процесс; classic initialize не выполнялся; инструмент " + ConfluenceTestData.LIST_SPACES_TOOL + " доступен для чтения.\nШаги: вызвать tools/call с name=" + ConfluenceTestData.LIST_SPACES_TOOL + ", arguments.limit=" + McpTestData.DEFAULT_LIMIT + " и protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + ".\nОжидаемый результат: JSON-RPC error отсутствует; result.isError=false; resultType=complete; content содержит хотя бы один текстовый элемент; serverInfo содержит ожидаемые имя и версию сервера.")
    @Tag("case-MCP-021")
    @Tag("smoke")
    void shouldCallReadToolStatelessWithoutInitialize() throws Exception {
        Allure.parameter("MCP method", "tools/call");
        Allure.parameter("MCP tool", ConfluenceTestData.LIST_SPACES_TOOL);
        Allure.parameter("arguments.limit", McpTestData.DEFAULT_LIMIT);
        Allure.parameter("MCP protocolVersion", McpTestData.STATELESS_PROTOCOL_VERSION);
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            ObjectNode params = OBJECT_MAPPER.createObjectNode();
            params.put("name", ConfluenceTestData.LIST_SPACES_TOOL);
            params.putObject("arguments").put("limit", McpTestData.DEFAULT_LIMIT);
            JsonNode response = Allure.step("Вызвать stateless tool " + ConfluenceTestData.LIST_SPACES_TOOL + " с arguments.limit=" + McpTestData.DEFAULT_LIMIT, () -> session.client().requestStateless(
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
            JsonNode content = response.path("result").path("content");
            assertThat(content.isArray())
                    .as("Успешный MCP tool result должен содержать массив content")
                    .isTrue();
            assertThat(content.size())
                    .as("Успешный MCP tool result должен содержать хотя бы один элемент content")
                    .isGreaterThan(0);
            assertThat(content.get(0).path("type").asText())
                    .as("Первый элемент MCP tool result должен иметь тип text")
                    .isEqualTo("text");
            assertThat(content.get(0).path("text").isTextual())
                    .as("Первый элемент MCP tool result должен содержать текст")
                    .isTrue();
            assertThat(response.path("result").path("_meta")
                    .path("io.modelcontextprotocol/serverInfo").path("name").asText())
                    .as("Stateless tools/call должен возвращать serverInfo с именем сервера")
                    .isEqualTo(McpTestData.SERVER_NAME);
            assertThat(response.path("result").path("_meta")
                    .path("io.modelcontextprotocol/serverInfo").path("version").asText())
                    .as("Stateless tools/call должен возвращать версию MCP-сервера в serverInfo")
                    .isEqualTo(McpTestData.SERVER_VERSION);
        }
    
    }

    @Test
    @Feature("Classic Lifecycle")
    @Story("MCP-036 — Обработка ping до и после READY")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("MCP-036: ping в NEW, INITIALIZING и READY возвращает пустой result; tools/list доступен только в READY")
    @Description("Предусловия: запущен новый MCP STDIO-процесс в состоянии NEW.\nШаги: (1) вызвать ping в NEW; (2) выполнить initialize с protocolVersion=" + McpTestData.CLASSIC_PROTOCOL_VERSION + "; (3) вызвать ping и tools/list в INITIALIZING; (4) отправить notifications/initialized; (5) повторить ping и tools/list в READY.\nОжидаемый результат: все три вызова ping завершаются успешно с пустым объектом result; tools/list в INITIALIZING возвращает error=" + McpTestData.MCP_SERVER_NOT_INITIALIZED + "; tools/list в READY возвращает массив tools.")
    @Tag("case-MCP-036")
    @Tag("smoke")
    void shouldHandlePingWithoutChangingLifecycleState() throws Exception {
        Allure.parameter("MCP method", McpTestData.PING_METHOD);
        Allure.parameter("Lifecycle states", "NEW; INITIALIZING; READY");
        Allure.parameter("Ожидаемый результат ping", "пустой объект");
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode pingBeforeInitialize =
                    sendRequest(session.client(), McpTestData.PING_METHOD, null, REQUEST_TIMEOUT);
            assertEmptyPingResult(pingBeforeInitialize);

            ObjectNode initializeParams = OBJECT_MAPPER.createObjectNode();
            initializeParams.put("protocolVersion", McpTestData.CLASSIC_PROTOCOL_VERSION);
            initializeParams.putObject("capabilities");
            initializeParams.putObject("clientInfo")
                    .put("name", McpTestData.CLIENT_NAME)
                    .put("version", McpTestData.CLIENT_VERSION);
            JsonNode initialize = sendRequest(session.client(), 
                    McpTestData.INITIALIZE_METHOD, initializeParams, REQUEST_TIMEOUT);
            assertThat(initialize.has("error"))
                    .as("Корректный initialize должен завершиться без JSON-RPC error")
                    .isFalse();

            JsonNode pingBeforeReady =
                    sendRequest(session.client(), McpTestData.PING_METHOD, null, REQUEST_TIMEOUT);
            assertEmptyPingResult(pingBeforeReady);

            JsonNode toolsBeforeReady =
                    sendRequest(session.client(), McpTestData.TOOLS_LIST_METHOD, null, REQUEST_TIMEOUT);
            assertThat(toolsBeforeReady.path("error").path("code").asInt())
                    .as("До notifications/initialized инструменты должны оставаться недоступными")
                    .isEqualTo(McpTestData.MCP_SERVER_NOT_INITIALIZED);

            Allure.step("Отправить notifications/initialized для перехода lifecycle в READY", () -> session.client().notification(McpTestData.INITIALIZED_NOTIFICATION_METHOD, null));

            JsonNode pingAfterReady =
                    sendRequest(session.client(), McpTestData.PING_METHOD, null, REQUEST_TIMEOUT);
            assertEmptyPingResult(pingAfterReady);

            JsonNode toolsAfterReady =
                    sendRequest(session.client(), McpTestData.TOOLS_LIST_METHOD, null, REQUEST_TIMEOUT);
            assertThat(toolsAfterReady.has("error"))
                    .as("После notifications/initialized tools/list должен выполняться успешно")
                    .isFalse();
            assertThat(toolsAfterReady.path("result").path("tools").isArray())
                    .as("После перехода в READY сервер должен вернуть массив инструментов")
                    .isTrue();
        }
    
    }

    @Step("Сравнить {label}: фактически={actual}, ожидается={expected}")
    private static <T> void assertEqualsStep(String label, T actual, T expected) {
        assertThat(actual)
                .as("%s должен совпадать с ожидаемым значением", label)
                .isEqualTo(expected);
    }

    @Step("Проверить условие «{label}»: фактически={actual}, ожидается=true")
    private static void assertTrueStep(String label, boolean actual) {
        assertThat(actual)
                .as("%s должно быть истинным", label)
                .isTrue();
    }

    @Step("Проверить условие «{label}»: фактически={actual}, ожидается=false")
    private static void assertFalseStep(String label, boolean actual) {
        assertThat(actual)
                .as("%s должно быть ложным", label)
                .isFalse();
    }

    @Step("Отправить JSON-RPC request: method={method}, timeout={timeout}")
    private static JsonNode sendRequest(ru.solomentsev.aqa.mcp.McpClient client,
                                        String method,
                                        ObjectNode params,
                                        Duration timeout) {
        return client.request(method, params, timeout);
    }

    @Step("Проверить, что ping возвращает пустой объект result")
    private static void assertEmptyPingResult(JsonNode response) {
        assertThat(response.has("error"))
                        .as("Ping должен завершаться без JSON-RPC error")
                        .isFalse();
        assertThat(response.path("result").isObject())
                .as("Ping должен возвращать объект result")
                .isTrue();
        assertThat(response.path("result").isEmpty())
                .as("Результат ping должен быть пустым объектом")
                .isTrue();
    }
}
