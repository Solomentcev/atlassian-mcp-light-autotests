package ru.solomentsev.aqa.tests.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;

import java.time.Duration;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ru.solomentsev.aqa.config.McpServerConfiguration;
import ru.solomentsev.aqa.mcp.McpProcessSession;
import ru.solomentsev.aqa.testdata.ConfluenceTestData;
import ru.solomentsev.aqa.testdata.McpContractTestData;
import ru.solomentsev.aqa.testdata.McpTestData;

import static ru.solomentsev.aqa.mcp.McpClientTestActions.sendRequest;
import static ru.solomentsev.aqa.support.mcp.McpContractTestSupport.textValues;
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
    @DisplayName("MCP-017: classic tools/list в состоянии NEW до initialize отклоняется")
    @Description("Предусловия: запущен новый MCP STDIO-процесс; initialize и notifications/initialized не отправлялись.\nШаги: отправить classic JSON-RPC request method=tools/list без stateless metadata.\nОжидаемый результат: сервер возвращает ошибку состояния «сервер ещё не инициализирован»; инструменты не перечисляются.")
    @Tag("case-MCP-017")
    void shouldRejectClassicToolsListBeforeInitialize() throws Exception {
        Allure.parameter("MCP method", McpTestData.getToolsListMethod());
        Allure.parameter("Lifecycle state", "NEW; initialize не выполнялся");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getMcpServerNotInitialized());
        try (McpProcessSession session = Allure.step(
                "Запустить новый MCP STDIO-процесс без classic initialize",
                () -> McpServerConfiguration.fromEnvironment().startStatelessSession())) {
            JsonNode response = sendRequest(session.client(), McpTestData.getToolsListMethod(), null, REQUEST_TIMEOUT);
            assertThat(response.has("error"))
                    .as("До initialize сервер должен вернуть JSON-RPC error")
                    .isTrue();
            assertThat(response.path("error").isObject())
                    .as("JSON-RPC error должен быть объектом")
                    .isTrue();
            assertThat(response.path("error").has("code"))
                    .as("JSON-RPC error должен содержать code")
                    .isTrue();
            assertThat(response.path("error").path("code").isIntegralNumber())
                    .as("JSON-RPC error.code должен быть целым числом")
                    .isTrue();
            assertThat(response.path("error").path("code").asInt())
                    .as("До initialize сервер должен отклонять tools/list с ошибкой состояния")
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
            assertThat(response.has("error"))
                    .as("Успешный server/discover не должен содержать JSON-RPC error")
                    .isFalse();
            assertThat(result.isObject())
                    .as("Успешный server/discover должен вернуть объект result")
                    .isTrue();
            assertThat(result.has("resultType"))
                    .as("Результат server/discover должен содержать resultType")
                    .isTrue();
            assertThat(result.path("resultType").isTextual())
                    .as("result.resultType должен быть строкой")
                    .isTrue();
            assertThat(result.path("resultType").asText())
                    .as("server/discover должен вернуть resultType=complete")
                    .isEqualTo(McpTestData.getCompleteResultType());

            JsonNode supportedVersions = result.path("supportedVersions");
            assertThat(supportedVersions.isArray())
                    .as("result.supportedVersions должен быть массивом")
                    .isTrue();
            for (JsonNode supportedVersion : supportedVersions) {
                assertThat(supportedVersion.isTextual())
                        .as("Каждая поддерживаемая версия протокола должна быть строкой")
                        .isTrue();
            }
            assertThat(textValues(supportedVersions))
                    .as("Список поддерживаемых protocolVersion должен совпадать с контрактом")
                    .containsExactlyElementsOf(McpTestData.getSupportedProtocolVersions());

            assertThat(result.path("capabilities").isObject())
                    .as("result.capabilities должен быть объектом")
                    .isTrue();
            assertThat(result.path("capabilities").has("tools"))
                    .as("result.capabilities должен содержать tools")
                    .isTrue();
            assertThat(result.path("capabilities").path("tools").isObject())
                    .as("result.capabilities.tools должен быть объектом")
                    .isTrue();
            assertThat(result.has("ttlMs"))
                    .as("result.ttlMs должен присутствовать")
                    .isTrue();
            assertThat(result.path("ttlMs").isIntegralNumber())
                    .as("result.ttlMs должен быть целым числом")
                    .isTrue();
            assertThat(result.path("ttlMs").asLong())
                    .as("result.ttlMs должен совпадать с контрактом")
                    .isEqualTo(McpTestData.getDiscoveryTtlMs());
            assertThat(result.has("cacheScope"))
                    .as("result должен содержать cacheScope")
                    .isTrue();
            assertThat(result.path("cacheScope").isTextual())
                    .as("result.cacheScope должен быть строкой")
                    .isTrue();
            assertThat(result.path("cacheScope").asText())
                    .as("result.cacheScope должен совпадать с контрактом")
                    .isEqualTo(McpTestData.getDiscoveryCacheScope());

            assertThat(result.path("_meta").isObject())
                    .as("result._meta должен быть объектом")
                    .isTrue();
            assertThat(result.path("_meta").has("io.modelcontextprotocol/serverInfo"))
                    .as("result._meta должен содержать serverInfo")
                    .isTrue();
            JsonNode serverInfo = result.path("_meta").path("io.modelcontextprotocol/serverInfo");
            assertThat(serverInfo.isObject())
                    .as("serverInfo должен быть объектом")
                    .isTrue();
            assertThat(serverInfo.has("name"))
                    .as("serverInfo должен содержать name")
                    .isTrue();
            assertThat(serverInfo.path("name").isTextual())
                    .as("serverInfo.name должен быть строкой")
                    .isTrue();
            assertThat(serverInfo.path("name").asText())
                    .as("serverInfo.name должен совпадать с контрактом")
                    .isEqualTo(McpTestData.getServerName());
            assertThat(serverInfo.has("version"))
                    .as("serverInfo должен содержать version")
                    .isTrue();
            assertThat(serverInfo.path("version").isTextual())
                    .as("serverInfo.version должен быть строкой")
                    .isTrue();
            assertThat(serverInfo.path("version").asText())
                    .as("serverInfo.version должен совпадать с контрактом")
                    .isEqualTo(McpTestData.getServerVersion());
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
        try (McpProcessSession session = Allure.step(
                "Запустить новый MCP STDIO-процесс без classic initialize",
                () -> McpServerConfiguration.fromEnvironment().startStatelessSession())) {
            JsonNode response = Allure.step("Выполнить tools/list в stateless-режиме: protocolVersion=" + McpTestData.getStatelessProtocolVersion(), () -> session.client().listToolsStateless(
                    McpTestData.getStatelessProtocolVersion(), REQUEST_TIMEOUT));
            assertThat(response.has("error"))
                    .as("Stateless MCP-запрос не должен завершаться JSON-RPC error")
                    .isFalse();
            JsonNode result = response.path("result");
            assertThat(result.isObject())
                    .as("Успешный stateless tools/list должен вернуть объект result")
                    .isTrue();
            assertThat(result.has("tools"))
                    .as("Stateless tools/list должен содержать поле tools")
                    .isTrue();
            JsonNode tools = result.path("tools");
            assertThat(tools.isArray())
                    .as("result.tools должен быть массивом")
                    .isTrue();
            assertThat(tools.size())
                    .as("Stateless tools/list должен вернуть все инструменты публичного контракта")
                    .isEqualTo(McpContractTestData.getExpectedTools().size());
            assertThat(result.has("resultType"))
                    .as("Stateless result должен содержать resultType")
                    .isTrue();
            assertThat(result.path("resultType").isTextual())
                    .as("result.resultType должен быть строкой")
                    .isTrue();
            assertThat(result.path("resultType").asText())
                    .as("Stateless-ответ должен иметь resultType=complete")
                    .isEqualTo(McpTestData.getCompleteResultType());
            assertThat(result.path("_meta").isObject())
                    .as("Stateless result._meta должен быть объектом")
                    .isTrue();
            assertThat(result.path("_meta").has("io.modelcontextprotocol/serverInfo"))
                    .as("Stateless result._meta должен содержать serverInfo")
                    .isTrue();
            JsonNode serverInfo = result.path("_meta").path("io.modelcontextprotocol/serverInfo");
            assertThat(serverInfo.isObject())
                    .as("serverInfo должен быть объектом")
                    .isTrue();
            assertThat(serverInfo.has("name"))
                    .as("serverInfo должен содержать name")
                    .isTrue();
            assertThat(serverInfo.path("name").isTextual())
                    .as("serverInfo.name должен быть строкой")
                    .isTrue();
            assertThat(serverInfo.path("name").asText())
                    .as("Stateless tools/list должен возвращать имя сервера по контракту")
                    .isEqualTo(McpTestData.getServerName());
            assertThat(serverInfo.has("version"))
                    .as("serverInfo должен содержать version")
                    .isTrue();
            assertThat(serverInfo.path("version").isTextual())
                    .as("serverInfo.version должен быть строкой")
                    .isTrue();
            assertThat(serverInfo.path("version").asText())
                    .as("Stateless tools/list должен возвращать версию сервера по контракту")
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
        try (McpProcessSession session = Allure.step(
                "Запустить новый MCP STDIO-процесс без classic initialize",
                () -> McpServerConfiguration.fromEnvironment().startStatelessSession())) {
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
                    .as("Stateless MCP-запрос не должен завершаться JSON-RPC error")
                    .isFalse();
            JsonNode result = response.path("result");
            assertThat(result.isObject())
                    .as("Успешный tools/call должен вернуть объект result")
                    .isTrue();
            assertThat(result.has("isError"))
                    .as("MCP tool result должен содержать isError")
                    .isTrue();
            assertThat(result.path("isError").isBoolean())
                    .as("MCP tool result.isError должен иметь boolean type")
                    .isTrue();
            assertThat(result.path("isError").asBoolean())
                    .as("Успешный read-инструмент не должен возвращать MCP tool error")
                    .isFalse();
            assertThat(result.has("resultType"))
                    .as("MCP tool result должен содержать resultType")
                    .isTrue();
            assertThat(result.path("resultType").isTextual())
                    .as("MCP tool result.resultType должен быть строкой")
                    .isTrue();
            assertThat(result.path("resultType").asText())
                    .as("Stateless-ответ должен иметь resultType=complete")
                    .isEqualTo(McpTestData.getCompleteResultType());
            assertThat(result.has("content"))
                    .as("MCP tool result должен содержать content")
                    .isTrue();
            JsonNode content = result.path("content");
            assertThat(content.isArray())
                    .as("Успешный MCP tool result должен содержать массив content")
                    .isTrue();
            assertThat(content.size())
                    .as("Успешный MCP tool result должен содержать хотя бы один элемент content")
                    .isGreaterThan(0);
            JsonNode firstContent = content.get(0);
            assertThat(firstContent.isObject())
                    .as("Элемент MCP tool content должен быть объектом")
                    .isTrue();
            assertThat(firstContent.has("type"))
                    .as("Элемент MCP tool content должен содержать type")
                    .isTrue();
            assertThat(firstContent.path("type").isTextual())
                    .as("content[0].type должен быть строкой")
                    .isTrue();
            assertThat(firstContent.path("type").asText())
                    .as("Первый элемент MCP tool result должен иметь тип text")
                    .isEqualTo("text");
            assertThat(firstContent.has("text"))
                    .as("Текстовый элемент MCP tool result должен содержать text")
                    .isTrue();
            assertThat(firstContent.path("text").isTextual())
                    .as("content[0].text должен быть строкой")
                    .isTrue();
            assertThat(firstContent.path("text").asText())
                    .as("Текстовый элемент MCP tool result не должен быть пустым")
                    .isNotBlank();
            assertThat(result.path("_meta").isObject())
                    .as("MCP tool result._meta должен быть объектом")
                    .isTrue();
            assertThat(result.path("_meta").has("io.modelcontextprotocol/serverInfo"))
                    .as("MCP tool result._meta должен содержать serverInfo")
                    .isTrue();
            JsonNode serverInfo = result.path("_meta").path("io.modelcontextprotocol/serverInfo");
            assertThat(serverInfo.isObject())
                    .as("serverInfo должен быть объектом")
                    .isTrue();
            assertThat(serverInfo.has("name"))
                    .as("serverInfo должен содержать name")
                    .isTrue();
            assertThat(serverInfo.path("name").isTextual())
                    .as("serverInfo.name должен быть строкой")
                    .isTrue();
            assertThat(serverInfo.path("name").asText())
                    .as("Stateless tools/call должен возвращать имя сервера по контракту")
                    .isEqualTo(McpTestData.getServerName());
            assertThat(serverInfo.has("version"))
                    .as("serverInfo должен содержать version")
                    .isTrue();
            assertThat(serverInfo.path("version").isTextual())
                    .as("serverInfo.version должен быть строкой")
                    .isTrue();
            assertThat(serverInfo.path("version").asText())
                    .as("Stateless tools/call должен возвращать версию сервера по контракту")
                    .isEqualTo(McpTestData.getServerVersion());
        }

    }

    @Test
    @Feature("Classic Lifecycle")
    @Story("MCP-036 — Обработка ping до и после READY")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("MCP-036: ping в NEW, INITIALIZING и READY возвращает пустой result; tools/list доступен только в READY")
    @Description("Предусловия: запущен новый MCP STDIO-процесс в состоянии NEW.\nШаги: (1) вызвать ping в NEW; (2) выполнить initialize с classic protocolVersion; (3) вызвать ping и tools/list в INITIALIZING; (4) отправить notifications/initialized; (5) повторить ping и tools/list в READY.\nОжидаемый результат: все три вызова ping завершаются успешно с пустым объектом result; tools/list в INITIALIZING возвращает ошибку состояния; tools/list в READY возвращает массив tools.")
    @Tag("case-MCP-036")
    @Tag("smoke")
    void shouldHandlePingWithoutChangingLifecycleState() throws Exception {
        Allure.parameter("MCP method", McpTestData.getPingMethod());
        Allure.parameter("Lifecycle states", "NEW; INITIALIZING; READY");
        Allure.parameter("Ожидаемый результат ping", "пустой объект");
        try (McpProcessSession session = Allure.step(
                "Запустить новый MCP STDIO-процесс без classic initialize",
                () -> McpServerConfiguration.fromEnvironment().startStatelessSession())) {
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
            assertThat(toolsBeforeReady.has("error"))
                    .as("До notifications/initialized tools/list должен вернуть JSON-RPC error")
                    .isTrue();
            assertThat(toolsBeforeReady.path("error").path("code").isIntegralNumber())
                    .as("JSON-RPC error.code должен быть целым числом")
                    .isTrue();
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

}
