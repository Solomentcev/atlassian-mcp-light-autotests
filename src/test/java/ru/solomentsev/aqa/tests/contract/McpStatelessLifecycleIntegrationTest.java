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
import ru.solomentsev.aqa.testdata.McpContractTestData;
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

    private static final Duration REQUEST_TIMEOUT = McpTestData.getRequestTimeout();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Feature("Classic Lifecycle")
    @Story("MCP-017 — Запрос до инициализации")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-017: classic tools/list в состоянии NEW до initialize возвращает error=" + -32001 + ")")
    @Description("Предусловия: запущен новый MCP STDIO-процесс; initialize и notifications/initialized не отправлялись.\nШаги: отправить classic JSON-RPC request method=tools/list без stateless metadata.\nОжидаемый результат: сервер возвращает JSON-RPC error code=" + -32001 + " (сервер ещё не инициализирован); инструменты не перечисляются.")
    @Tag("case-MCP-017")
    void shouldRejectClassicToolsListBeforeInitialize() throws Exception {
        Allure.parameter("MCP method", McpTestData.getToolsListMethod());
        Allure.parameter("Lifecycle state", "NEW; initialize не выполнялся");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getMcpServerNotInitialized());
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode response = sendRequest(session.client(), McpTestData.getToolsListMethod(), null, REQUEST_TIMEOUT);
            assertThat(response.path("error").path("code").asInt())
                    .as("До initialize сервер должен отклонять tools/list с ошибкой состояния " + McpTestData.getMcpServerNotInitialized())
                    .isEqualTo(McpTestData.getMcpServerNotInitialized());
        }
    
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-019 — Обнаружение возможностей stateless-режима")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-019: server/discover без initialize возвращает capabilities и serverInfo")
    @Description("Предусловия: запущен новый MCP STDIO-процесс; classic initialize не выполнялся.\nШаги: отправить server/discover с protocolVersion и clientCapabilities; проверить supportedVersions, capabilities.tools, ttlMs, cacheScope и serverInfo.\nОжидаемый результат: JSON-RPC error отсутствует; resultType=complete; supportedVersions содержит поддерживаемые версии; tools capability объявлена; TTL, cacheScope и serverInfo соответствуют контракту.")
    @Tag("case-MCP-019")
    @Tag("smoke")
    void shouldDiscoverStatelessCapabilitiesWithoutInitialize() throws Exception {
        Allure.parameter("MCP method", McpTestData.getServerDiscoverMethod());
        Allure.parameter("MCP protocolVersion", McpTestData.getStatelessProtocolVersion());

        McpProcessSession session = Allure.step(
                "Запустить новый MCP STDIO-процесс без classic initialize",
                () -> McpServerConfiguration.fromEnvironment().startStatelessSession());
        try (session) {
            JsonNode response = Allure.step(
                    "Отправить server/discover: protocolVersion=" + McpTestData.getStatelessProtocolVersion(),
                    () -> session.client().discoverStateless(
                            McpTestData.getStatelessProtocolVersion(), REQUEST_TIMEOUT));

            JsonNode result = response.path("result");
            assertThat(response.has("error")).as("JSON-RPC response не содержит error" + " должно быть ложным").isFalse();
            assertThat(result.path("resultType").asText()).as("result.resultType" + " должен совпадать с ожидаемым значением").isEqualTo("complete");

            JsonNode supportedVersions = result.path("supportedVersions");
            assertThat(supportedVersions.isArray()).as("result.supportedVersions является массивом" + " должно быть истинным").isTrue();
            assertThat(supportedVersions.size()).as("Количество поддерживаемых версий" + " должен совпадать с ожидаемым значением").isEqualTo(3);
            assertThat(supportedVersions.get(0).asText()).as("supportedVersions[0] — stateless protocolVersion" + " должен совпадать с ожидаемым значением").isEqualTo(McpTestData.getStatelessProtocolVersion());
            assertThat(supportedVersions.get(1).asText()).as("supportedVersions[1] — classic protocolVersion" + " должен совпадать с ожидаемым значением").isEqualTo(McpTestData.getClassicProtocolVersion());
            assertThat(supportedVersions.get(2).asText()).as("supportedVersions[2] — legacy protocolVersion" + " должен совпадать с ожидаемым значением").isEqualTo(McpTestData.getLegacyProtocolVersion());

            boolean toolsCapabilityIsObject = result.path("capabilities").path("tools").isObject();
            assertThat(toolsCapabilityIsObject).as("result.capabilities.tools является объектом" + " должно быть истинным").isTrue();
            assertThat(result.path("ttlMs").asLong()).as("result.ttlMs" + " должен совпадать с ожидаемым значением").isEqualTo(McpTestData.getDiscoveryTtlMs());
            assertThat(result.path("cacheScope").asText()).as("result.cacheScope" + " должен совпадать с ожидаемым значением").isEqualTo(McpTestData.getDiscoveryCacheScope());

            JsonNode serverInfo = result.path("_meta").path("io.modelcontextprotocol/serverInfo");
            assertThat(serverInfo.path("name").asText()).as("serverInfo.name" + " должен совпадать с ожидаемым значением").isEqualTo(McpTestData.getServerName());
            assertThat(serverInfo.path("version").asText()).as("serverInfo.version" + " должен совпадать с ожидаемым значением").isEqualTo(McpTestData.getServerVersion());
        }
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-020 — Вызов tools/list в stateless-режиме без initialize")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-020: stateless tools/list без initialize возвращает инструменты по контракту")
    @Description("Предусловия: запущен новый MCP STDIO-процесс; classic initialize не выполнялся.\nШаги: отправить stateless tools/list с обязательными metadata; проверить result.tools, resultType и serverInfo.\nОжидаемый результат: JSON-RPC error отсутствует; result.tools соответствует публичному контракту; resultType=complete; serverInfo содержит метаданные сервера.")
    @Tag("case-MCP-020")
    @Tag("smoke")
    void shouldListToolsStatelessWithoutInitialize() throws Exception {
        Allure.parameter("MCP method", McpTestData.getToolsListMethod());
        Allure.parameter("MCP protocolVersion", McpTestData.getStatelessProtocolVersion());
        Allure.parameter("Ожидаемое количество инструментов", McpContractTestData.getExpectedTools().size());
        Allure.parameter("Classic initialize", "не выполняется");
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode response = Allure.step("Выполнить tools/list в stateless-режиме: protocolVersion=" + McpTestData.getStatelessProtocolVersion(), () -> session.client().listToolsStateless(
                    McpTestData.getStatelessProtocolVersion(), REQUEST_TIMEOUT));
            assertThat(response.has("error"))
                    .as("Stateless MCP-запрос должен завершиться без JSON-RPC error")
                    .isFalse();
            assertThat(response.path("result").path("tools").size())
                    .as("Stateless tools/list должен вернуть все инструменты, объявленные в публичном контракте")
                    .isEqualTo(McpTestData.getExpectedToolCount());
            assertThat(response.path("result").path("resultType").asText())
                    .as("Stateless-ответ должен иметь resultType=complete")
                    .isEqualTo("complete");
            assertThat(response.path("result").path("_meta")
                    .path("io.modelcontextprotocol/serverInfo").path("name").asText())
                    .as("Stateless tools/list должен возвращать serverInfo с именем сервера")
                    .isEqualTo(McpTestData.getServerName());
            assertThat(response.path("result").path("_meta")
                    .path("io.modelcontextprotocol/serverInfo").path("version").asText())
                    .as("Stateless tools/list должен возвращать версию MCP-сервера в serverInfo")
                    .isEqualTo(McpTestData.getServerVersion());
        }
    
    }

    @Test
    @Feature("Stateless Lifecycle")
    @Story("MCP-021 — Вызов tools/call в stateless-режиме")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-021: stateless tools/call read-инструмента без initialize")
    @Description("Предусловия: запущен новый MCP STDIO-процесс; classic initialize не выполнялся; доступен read-инструмент Confluence.\nШаги: вызвать tools/call с допустимыми arguments и обязательными stateless metadata.\nОжидаемый результат: JSON-RPC error отсутствует; result.isError=false; resultType=complete; content содержит текстовый элемент; serverInfo соответствует контракту.")
    @Tag("case-MCP-021")
    @Tag("smoke")
    void shouldCallReadToolStatelessWithoutInitialize() throws Exception {
        Allure.parameter("MCP method", McpTestData.getToolsCallMethod());
        Allure.parameter("MCP tool", ConfluenceTestData.getListSpacesTool());
        Allure.parameter("arguments.limit", McpTestData.getDefaultLimit());
        Allure.parameter("MCP protocolVersion", McpTestData.getStatelessProtocolVersion());
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            ObjectNode params = OBJECT_MAPPER.createObjectNode();
            params.put("name", ConfluenceTestData.getListSpacesTool());
            params.putObject("arguments").put("limit", McpTestData.getDefaultLimit());
            JsonNode response = Allure.step("Вызвать stateless tool " + ConfluenceTestData.getListSpacesTool() + " с arguments.limit=" + McpTestData.getDefaultLimit(), () -> session.client().requestStateless(
                    McpTestData.getToolsCallMethod(),
                    params,
                    McpTestData.getStatelessProtocolVersion(),
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
                    .isEqualTo(McpTestData.getServerName());
            assertThat(response.path("result").path("_meta")
                    .path("io.modelcontextprotocol/serverInfo").path("version").asText())
                    .as("Stateless tools/call должен возвращать версию MCP-сервера в serverInfo")
                    .isEqualTo(McpTestData.getServerVersion());
        }
    
    }

    @Test
    @Feature("Classic Lifecycle")
    @Story("MCP-036 — Обработка ping до и после READY")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("MCP-036: ping в NEW, INITIALIZING и READY возвращает пустой result; tools/list доступен только в READY")
    @Description("Предусловия: запущен новый MCP STDIO-процесс в состоянии NEW.\nШаги: (1) вызвать ping в NEW; (2) выполнить initialize с protocolVersion=" + "2025-11-25" + "; (3) вызвать ping и tools/list в INITIALIZING; (4) отправить notifications/initialized; (5) повторить ping и tools/list в READY.\nОжидаемый результат: все три вызова ping завершаются успешно с пустым объектом result; tools/list в INITIALIZING возвращает error=" + -32001 + "; tools/list в READY возвращает массив tools.")
    @Tag("case-MCP-036")
    @Tag("smoke")
    void shouldHandlePingWithoutChangingLifecycleState() throws Exception {
        Allure.parameter("MCP method", McpTestData.getPingMethod());
        Allure.parameter("Lifecycle states", "NEW; INITIALIZING; READY");
        Allure.parameter("Ожидаемый результат ping", "пустой объект");
        try (McpProcessSession session =
                     McpServerConfiguration.fromEnvironment().startStatelessSession()) {
            JsonNode pingBeforeInitialize =
                    sendRequest(session.client(), McpTestData.getPingMethod(), null, REQUEST_TIMEOUT);
            assertThat(pingBeforeInitialize.has("error"))
                    .as("Ping не должен возвращать JSON-RPC error")
                    .isFalse();
            assertThat(pingBeforeInitialize.path("result").isObject())
                    .as("Ping должен возвращать объект result")
                    .isTrue();
            assertThat(pingBeforeInitialize.path("result").isEmpty())
                    .as("Ping должен возвращать пустой объект result")
                    .isTrue();

            ObjectNode initializeParams = OBJECT_MAPPER.createObjectNode();
            initializeParams.put("protocolVersion", McpTestData.getClassicProtocolVersion());
            initializeParams.putObject("capabilities");
            initializeParams.putObject("clientInfo")
                    .put("name", McpTestData.getClientName())
                    .put("version", McpTestData.getClientVersion());
            JsonNode initialize = sendRequest(session.client(), 
                    McpTestData.getInitializeMethod(), initializeParams, REQUEST_TIMEOUT);
            assertThat(initialize.has("error"))
                    .as("Корректный initialize должен завершиться без JSON-RPC error")
                    .isFalse();

            JsonNode pingBeforeReady =
                    sendRequest(session.client(), McpTestData.getPingMethod(), null, REQUEST_TIMEOUT);
            assertThat(pingBeforeReady.has("error"))
                    .as("Ping не должен возвращать JSON-RPC error")
                    .isFalse();
            assertThat(pingBeforeReady.path("result").isObject())
                    .as("Ping должен возвращать объект result")
                    .isTrue();
            assertThat(pingBeforeReady.path("result").isEmpty())
                    .as("Ping должен возвращать пустой объект result")
                    .isTrue();

            JsonNode toolsBeforeReady =
                    sendRequest(session.client(), McpTestData.getToolsListMethod(), null, REQUEST_TIMEOUT);
            assertThat(toolsBeforeReady.path("error").path("code").asInt())
                    .as("До notifications/initialized инструменты должны оставаться недоступными")
                    .isEqualTo(McpTestData.getMcpServerNotInitialized());

            Allure.step("Отправить notifications/initialized для перехода lifecycle в READY", () -> session.client().notification(McpTestData.getInitializedNotificationMethod(), null));

            JsonNode pingAfterReady =
                    sendRequest(session.client(), McpTestData.getPingMethod(), null, REQUEST_TIMEOUT);
            assertThat(pingAfterReady.has("error"))
                    .as("Ping не должен возвращать JSON-RPC error")
                    .isFalse();
            assertThat(pingAfterReady.path("result").isObject())
                    .as("Ping должен возвращать объект result")
                    .isTrue();
            assertThat(pingAfterReady.path("result").isEmpty())
                    .as("Ping должен возвращать пустой объект result")
                    .isTrue();

            JsonNode toolsAfterReady =
                    sendRequest(session.client(), McpTestData.getToolsListMethod(), null, REQUEST_TIMEOUT);
            assertThat(toolsAfterReady.has("error"))
                    .as("После notifications/initialized tools/list должен выполняться успешно")
                    .isFalse();
            assertThat(toolsAfterReady.path("result").path("tools").isArray())
                    .as("После перехода в READY сервер должен вернуть массив инструментов")
                    .isTrue();
        }
    
    }

    @Step("Отправить JSON-RPC request: method={method}, timeout={timeout}")
    private static JsonNode sendRequest(ru.solomentsev.aqa.mcp.McpClient client,
                                        String method,
                                        ObjectNode params,
                                        Duration timeout) {
        return client.request(method, params, timeout);
    }

}
