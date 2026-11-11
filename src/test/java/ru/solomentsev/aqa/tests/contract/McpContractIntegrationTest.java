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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.solomentsev.aqa.support.junit.McpServerTest;
import ru.solomentsev.aqa.mcp.McpClient;
import ru.solomentsev.aqa.testdata.McpTestData;
import ru.solomentsev.aqa.testdata.JiraTestData;
import ru.solomentsev.aqa.testdata.ConfluenceTestData;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThat;

/**
 * Проверяет публичный stateless MCP contract работающего тестового сервера.
 *
 * <p>Запускается отдельно с Maven-профилем {@code integration} и требует
 * настроенного тестового экземпляра Atlassian MCP Light.</p>
 */
@McpServerTest
@Tag("integration")
@EnabledIfSystemProperty(named = "AQA_RUN_INTEGRATION", matches = "true")
@Epic("MCP Server")
@Tag("contract")
@Tag("protocol")
@Tag("regression")
class McpContractIntegrationTest {

    private static final Duration REQUEST_TIMEOUT = McpTestData.REQUEST_TIMEOUT;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, ToolContract> EXPECTED_TOOLS = expectedTools();

    @Test
    @Feature("Tools Discovery")
    @Story("MCP-005 — Получение списка инструментов")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("MCP-005: tools/list возвращает массив инструментов [protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + "]")
    @Description("Предусловия: тестовый MCP-сервер запущен; доступен stateless tools/list с protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + ".\nШаги: отправить tools/list с обязательными stateless metadata; проверить JSON-RPC response и поле result.tools.\nОжидаемый результат: response не содержит error; result является объектом; result.tools является массивом.")    @Tag("case-MCP-005")
    @Tag("smoke")
    void shouldReturnToolsList(McpClient client) {
        JsonNode tools = getStatelessTools(client);
        assertThat(tools.isArray()).as("tools/list должен вернуть массив tools").isTrue();
    
    }

    @Test
    @Feature("Tools Discovery")
    @Story("MCP-006 — Проверка состава инструментов")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("MCP-006: Состав tools/list совпадает с контрактом [ожидается " + McpTestData.EXPECTED_TOOL_COUNT + " инструментов]")
    @Description("Предусловия: MCP-сервер отвечает на stateless tools/list.\nШаги: извлечь name каждого элемента result.tools; сравнить количество и полный набор имён с EXPECTED_TOOLS.\nОжидаемый результат: возвращено ровно " + McpTestData.EXPECTED_TOOL_COUNT + " уникальных имён; набор полностью совпадает с " + McpTestData.EXPECTED_TOOL_COUNT + " инструментами, перечисленными в контракте.")    @Tag("case-MCP-006")
    @Tag("smoke")
    void shouldExposeExpectedToolNames(McpClient client) {
        JsonNode tools = getStatelessTools(client);
        Set<String> actualNames = new HashSet<>();
        for (JsonNode tool : tools) {
            actualNames.add(tool.path("name").asText());
        }
        assertThat(tools.size()).as("Ожидалось %d инструментов по контракту, фактически получено %d", EXPECTED_TOOLS.size(), tools.size()).isEqualTo(EXPECTED_TOOLS.size());
        assertThat(actualNames).as("Имена tools должны совпадать с контрактом; фактически получены: %s", actualNames).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS.keySet());
    
    }

    @Test
    @Feature("Tools Discovery")
    @Story("MCP-007 — Проверка описаний инструментов")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-007: У каждого из " + McpTestData.EXPECTED_TOOL_COUNT + " инструментов есть непустой description")
    @Description("Предусловия: MCP-сервер вернул массив инструментов через tools/list.\nШаги: для каждого элемента result.tools прочитать name и description.\nОжидаемый результат: у каждого инструмента description является непустой строкой; при нарушении сообщение указывает имя конкретного инструмента.")    @Tag("case-MCP-007")
    void shouldExposeNonEmptyToolDescriptions(McpClient client) {
        JsonNode tools = getStatelessTools(client);
        for (JsonNode tool : tools) {
            assertThat(tool.path("description").asText())
                    .as("Инструмент %s должен содержать непустое описание", tool.path("name").asText())
                    .isNotBlank();
        }
    
    }

    @Test
    @Feature("Tool Schemas")
    @Story("MCP-008 — Проверка JSON Schema и annotations инструментов")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-008: JSON Schema и annotations всех " + McpTestData.EXPECTED_TOOL_COUNT + " инструментов соответствуют контракту")
    @Description("Предусловия: MCP-сервер вернул инструменты через stateless tools/list; в тесте задан ожидаемый контракт " + McpTestData.EXPECTED_TOOL_COUNT + " инструментов.\nШаги: для каждого инструмента сравнить inputSchema.type, properties, required, additionalProperties, типы и ограничения аргументов; затем проверить readOnlyHint, destructiveHint, idempotentHint и openWorldHint.\nОжидаемый результат: набор инструментов, схемы аргументов и все четыре annotation-флага каждого инструмента совпадают с ожидаемым контрактом; сообщение об ошибке указывает имя инструмента и проверяемое поле.")    @Tag("case-MCP-008")
    @Tag("jira")
    @Tag("confluence")
    void shouldExposeExactToolSchemasAndAnnotations(McpClient client) {
        JsonNode tools = getStatelessTools(client);
        for (JsonNode tool : tools) {
            String name = tool.path("name").asText();
            ToolContract expected = EXPECTED_TOOLS.get(name);
            assertThat(expected).as("Инструмент %s должен быть объявлен в ожидаемом контракте", name).isNotNull();

            JsonNode schema = tool.path("inputSchema");
            assertThat(schema.path("type").asText()).as("%s: корень inputSchema должен иметь type=object", name).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean()).as("%s: additionalProperties должен быть false", name).isFalse();
            assertThat(schema.path("properties").isObject()).as("%s: inputSchema должен содержать объект properties", name).isTrue();
            assertThat(fieldNames(schema.path("properties"))).as("%s: набор аргументов должен совпадать с контрактом", name)
                    .containsExactlyInAnyOrderElementsOf(expected.getPropertyTypes().keySet());

            if (expected.getRequired().isEmpty()) {
                assertThat(schema.has("required")).as("%s: поле required не должно присутствовать при пустом ожидаемом списке", name).isFalse();
            } else {
                assertThat(textValues(schema.path("required"))).as("%s: список required должен совпадать с контрактом", name)
                        .containsExactlyInAnyOrderElementsOf(expected.getRequired());
            }

            for (Map.Entry<String, String> property : expected.getPropertyTypes().entrySet()) {
                JsonNode definition = schema.path("properties").path(property.getKey());
                assertThat(definition.path("type").asText())
                        .as("%s.%s: ожидаемый type=%s", name, property.getKey(), property.getValue())
                        .isEqualTo(property.getValue());
                if ("string".equals(property.getValue())) {
                    assertThat(definition.path("minLength").asInt())
                            .as("%s.%s: ожидаемый minLength=%d", name, property.getKey(), McpTestData.MIN_STRING_LENGTH)
                            .isEqualTo(McpTestData.MIN_STRING_LENGTH);
                }
                if ("integer".equals(property.getValue())) {
                    assertThat(definition.path("minimum").asInt())
                            .as("%s.%s: ожидаемый minimum=%d", name, property.getKey(), McpTestData.MIN_LIMIT)
                            .isEqualTo(McpTestData.MIN_LIMIT);
                    if ("limit".equals(property.getKey())) {
                        assertThat(definition.path("maximum").asInt())
                                .as("%s.%s: ожидаемый maximum=%d", name, property.getKey(), McpTestData.MAX_LIMIT)
                                .isEqualTo(McpTestData.MAX_LIMIT);
                    }
                }
            }
            assertAnnotations(name, tool.path("annotations"), expected);
        }
    
    }

    @Test
    @Feature("Stateless Protocol")
    @Story("MCP-023 — Проверка обязательных stateless metadata")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-023: Stateless-запросы без _meta, clientCapabilities или protocolVersion отклоняются с error=-32602")
    @Description("Предусловия: MCP STDIO-процесс запущен и прошёл classic initialize; проверяются обязательные metadata отдельных stateless-запросов.\nШаги: вызвать server/discover без params._meta; вызвать tools/list с protocolVersion, но без clientCapabilities; вызвать tools/list с clientCapabilities, но без protocolVersion; затем выполнить корректный tools/list.\nОжидаемый результат: каждый из трёх некорректных запросов возвращает JSON-RPC error code=-32602; контрольный tools/list с protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + " выполняется успешно и возвращает массив tools.")
    @Tag("negative")
    @Tag("case-MCP-023")
    void shouldRejectMissingRequiredStatelessMetadata(McpClient client) {
        Allure.parameter("Stateless metadata scenarios", "без _meta; без clientCapabilities; без protocolVersion");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.JSON_RPC_INVALID_PARAMS);
        JsonNode missingMetadata = Allure.step("Вызвать server/discover без обязательного params._meta", () -> sendRequest(client, McpTestData.SERVER_DISCOVER_METHOD, null));
        assertThat(missingMetadata.path("error").path("code").asInt()).as("server/discover без обязательного _meta должен вернуть JSON-RPC error code=-32602, фактически: %s", missingMetadata.path("error").path("code")).isEqualTo(McpTestData.JSON_RPC_INVALID_PARAMS);

        ObjectNode missingCapabilitiesParams = OBJECT_MAPPER.createObjectNode();
        missingCapabilitiesParams.putObject("_meta")
                .put("io.modelcontextprotocol/protocolVersion", McpTestData.STATELESS_PROTOCOL_VERSION);
        JsonNode missingCapabilities = Allure.step("Вызвать tools/list с protocolVersion, но без clientCapabilities",
                () -> sendRequest(client, McpTestData.TOOLS_LIST_METHOD, missingCapabilitiesParams));
        assertThat(missingCapabilities.path("error").path("code").asInt()).as("tools/list с protocolVersion=%s, но без clientCapabilities должен вернуть -32602, фактически: %s", McpTestData.STATELESS_PROTOCOL_VERSION, missingCapabilities.path("error").path("code")).isEqualTo(McpTestData.JSON_RPC_INVALID_PARAMS);

        ObjectNode missingVersionParams = OBJECT_MAPPER.createObjectNode();
        missingVersionParams.putObject("_meta")
                .putObject("io.modelcontextprotocol/clientCapabilities");
        JsonNode missingVersion = Allure.step("Вызвать tools/list с clientCapabilities, но без protocolVersion",
                () -> sendRequest(client, McpTestData.TOOLS_LIST_METHOD, missingVersionParams));
        assertThat(missingVersion.path("error").path("code").asInt()).as("tools/list без protocolVersion должен вернуть -32602, фактически: %s", missingVersion.path("error").path("code")).isEqualTo(McpTestData.JSON_RPC_INVALID_PARAMS);

        JsonNode validResponse = Allure.step(
                "Повторить tools/list с обязательными metadata и protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION,
                () -> client.listToolsStateless(McpTestData.STATELESS_PROTOCOL_VERSION, REQUEST_TIMEOUT));
        assertSuccessfulJsonRpc(validResponse);
        assertThat(validResponse.path("result").path("tools").isArray())
                .as("Контрольный stateless tools/list должен вернуть массив result.tools после трёх отклонённых запросов")
                .isTrue();
    
    }

    @Test
    @Feature("Stateless Protocol")
    @Story("MCP-022 — Отклонение неподдерживаемой stateless protocolVersion")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-022: server/discover отклоняет protocolVersion=" + McpTestData.UNSUPPORTED_PROTOCOL_VERSION + " [error=-32022]")
    @Description("Предусловия: MCP STDIO-процесс запущен и готов принимать JSON-RPC запросы.\nШаги: вызвать server/discover с protocolVersion=" + McpTestData.UNSUPPORTED_PROTOCOL_VERSION + " и непустым clientCapabilities.\nОжидаемый результат: сервер возвращает JSON-RPC error code=-32022; error.data.supportedVersions содержит поддерживаемую версию " + McpTestData.STATELESS_PROTOCOL_VERSION + ".")
    @Tag("negative")
    @Tag("case-MCP-022")
    void shouldRejectUnsupportedStatelessProtocolVersion(McpClient client) {
        Allure.parameter("Переданный protocolVersion", McpTestData.UNSUPPORTED_PROTOCOL_VERSION);
        Allure.parameter("Поддерживаемый protocolVersion", McpTestData.STATELESS_PROTOCOL_VERSION);
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        ObjectNode metadata = params.putObject("_meta");
        metadata.put("io.modelcontextprotocol/protocolVersion", McpTestData.UNSUPPORTED_PROTOCOL_VERSION);
        metadata.putObject("io.modelcontextprotocol/clientCapabilities");

        JsonNode response = Allure.step("Вызвать server/discover с protocolVersion=" + McpTestData.UNSUPPORTED_PROTOCOL_VERSION,
                () -> sendRequest(client, McpTestData.SERVER_DISCOVER_METHOD, params));

        assertThat(response.path("error").path("code").asInt()).as("protocolVersion=%s должна завершаться ошибкой -32022", McpTestData.UNSUPPORTED_PROTOCOL_VERSION).isEqualTo(McpTestData.MCP_UNSUPPORTED_PROTOCOL_VERSION);
        assertThat(textValues(response.path("error").path("data").path("supportedVersions"))).as("В ошибке должны быть перечислены поддерживаемые версии протокола")
                .containsExactly(McpTestData.STATELESS_PROTOCOL_VERSION);
    
    
    }

    @Test
    @Feature("Tool Calls")
    @Story("MCP-010 — Вызов неизвестного инструмента")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-010: tools/call неизвестного инструмента [name=aqa_unknown_tool] возвращает MCP tool error")
    @Description("Предусловия: MCP-сервер запущен; stateless-вызов tools/call доступен.\nШаги: вызвать tools/call с name=aqa_unknown_tool и arguments={}.\nОжидаемый результат: JSON-RPC response содержит result.isError=true и текстовую диагностику в result.content; верхнеуровневое поле error отсутствует.")
    @Tag("negative")
    @Tag("case-MCP-010")
    void shouldReturnToolErrorForUnknownToolWithoutJsonRpcError(McpClient client) {
        Allure.parameter("MCP method", McpTestData.TOOLS_CALL_METHOD);
        Allure.parameter("Tool name", "aqa_unknown_tool");
        JsonNode response = Allure.step("Вызвать неизвестный инструмент name=aqa_unknown_tool с пустыми arguments",
                () -> callTool(client, "aqa_unknown_tool", OBJECT_MAPPER.createObjectNode()));

        assertToolError(response, "tools/call name=aqa_unknown_tool, arguments={}");
    
    
    }

    @Test
    @Feature("Tool Validation")
    @Story("MCP-011 — Вызов инструмента без обязательного аргумента")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-011: jira_get_issue без обязательного issue_key [arguments={}] возвращает MCP tool error")
    @Description("Предусловия: MCP-сервер запущен; jira_get_issue требует обязательный аргумент issue_key.\nШаги: вызвать stateless tools/call с name=jira_get_issue и arguments={}, то есть без issue_key.\nОжидаемый результат: JSON-RPC response содержит result.isError=true; result.content[0].text содержит текстовую диагностику; верхнеуровневое поле error отсутствует.")
    @Tag("negative")
    @Tag("case-MCP-011")
    @Tag("jira")
    void shouldRejectMissingRequiredToolArgument(McpClient client) {
        Allure.parameter("MCP tool", "jira_get_issue");
        Allure.parameter("Обязательный аргумент", "issue_key");
        Allure.parameter("Переданные arguments", "{}");
        JsonNode response = Allure.step("Вызвать jira_get_issue без обязательного аргумента issue_key",
                () -> callTool(client, "jira_get_issue", OBJECT_MAPPER.createObjectNode()));

        assertToolError(response, "jira_get_issue без обязательного issue_key (arguments={})");
    
    
    }

    @ParameterizedTest(name = "MCP-034: jira_get_issue отклоняет issue_key={0}")
    @MethodSource("requiredArgumentCases")
    @Feature("Tool Validation")
    @Story("MCP-034 — Валидация пустых и null значений аргументов")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-034: jira_get_issue отклоняет пустые и null значения issue_key")
    @Description("Предусловия: MCP-сервер запущен; доступен stateless-вызов jira_get_issue.\nШаги: передать issue_key с одним из некорректных значений.\nОжидаемый результат: каждый некорректный аргумент приводит к MCP tool error (result.isError=true); обращения к Jira с корректным ключом не требуется.")
    @Tag("negative")
    @Tag("case-MCP-034")
    @Tag("jira")
    void shouldRejectNullEmptyAndBlankRequiredArguments(String scenario,
                                                        String value,
                                                        McpClient client) {
        Allure.parameter("MCP tool", "jira_get_issue");
        Allure.parameter("Аргумент", "issue_key");
        Allure.parameter("Проверяемое значение", scenario);

        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        if (value == null) {
            arguments.putNull("issue_key");
        } else {
            arguments.put("issue_key", value);
        }

        JsonNode response = Allure.step(
                "Вызвать jira_get_issue с issue_key=" + scenario,
                () -> callTool(client, "jira_get_issue", arguments));
        assertToolError(response, "jira_get_issue с issue_key=" + scenario);
    }

    private static Stream<Arguments> requiredArgumentCases() {
        return Stream.of(
                Arguments.of("null", (String) null),
                Arguments.of("пустая строка", ""),
                Arguments.of("строка из трёх пробелов", "   ")
        );
    }

    @Test
    @Feature("Tool Validation")
    @Story("MCP-012 — Вызов инструмента с неверным типом аргумента")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-012: jira_get_issue отклоняет issue_key=" + JiraTestData.INVALID_ISSUE_KEY_NUMBER + " (integer вместо string)")
    @Description("Предусловия: MCP-сервер запущен; схема jira_get_issue объявляет issue_key строковым обязательным аргументом.\nШаги: вызвать jira_get_issue с issue_key=" + JiraTestData.INVALID_ISSUE_KEY_NUMBER + " (JSON integer).\nОжидаемый результат: вызов с аргументом неверного JSON-типа возвращает MCP tool error (result.isError=true); проверка не зависит от наличия конкретной задачи в Jira.")
    @Tag("negative")
    @Tag("case-MCP-012")
    @Tag("jira")
    void shouldRejectWrongToolArgumentType(McpClient client) {
        Allure.parameter("MCP tool", "jira_get_issue");
        Allure.parameter("Аргумент issue_key", JiraTestData.INVALID_ISSUE_KEY_NUMBER + " (число вместо строки)");
        Allure.parameter("Ожидаемый тип issue_key", "string");
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("issue_key", JiraTestData.INVALID_ISSUE_KEY_NUMBER);
        JsonNode invalidResponse = Allure.step("Вызвать jira_get_issue с issue_key=" + JiraTestData.INVALID_ISSUE_KEY_NUMBER + " (integer вместо string)",
                () -> callTool(client, "jira_get_issue", arguments));
        assertToolError(invalidResponse, "jira_get_issue с issue_key=" + JiraTestData.INVALID_ISSUE_KEY_NUMBER + " (integer вместо string)");

    
    }

    @Test
    @Feature("Tool Validation")
    @Story("MCP-013 — Вызов инструмента с неизвестным аргументом")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("MCP-013: jira_get_issue отклоняет неизвестный аргумент unexpected=value")
    @Description("Предусловия: MCP-сервер запущен; схема jira_get_issue содержит только объявленный аргумент issue_key.\nШаги: вызвать jira_get_issue с непустым строковым issue_key и дополнительным unexpected=value.\nОжидаемый результат: вызов возвращает MCP tool error (result.isError=true), так как additionalProperties=false; результат не зависит от существования задачи с переданным ключом.")
    @Tag("negative")
    @Tag("case-MCP-013")
    @Tag("jira")
    void shouldRejectUnknownToolArgument(McpClient client) {
        Allure.parameter("MCP tool", "jira_get_issue");
        Allure.parameter("Неизвестный аргумент", "unexpected=value");
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("issue_key", JiraTestData.ISSUE_KEY);
        arguments.put("unexpected", "value");
        JsonNode invalidResponse = Allure.step("Вызвать jira_get_issue с неизвестным аргументом unexpected=value",
                () -> callTool(client, "jira_get_issue", arguments));
        assertToolError(invalidResponse, "jira_get_issue с дополнительным аргументом unexpected=value");

    
    }

    @ParameterizedTest(name = "MCP-028: {0} проверяет границы limit")
    @MethodSource("limitBoundaryCases")
    @Feature("Tool Validation")
    @Story("MCP-028 — Проверка границ параметра limit")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-028: инструменты отклоняют limit вне диапазона и принимают допустимое значение")
    @Description("Предусловия: MCP-сервер запущен; доступны инструменты поиска Jira и Confluence.\nШаги: для выбранного инструмента выполнить вызовы с limit ниже минимума и выше максимума, затем с допустимым limit.\nОжидаемый результат: недопустимые значения возвращают MCP tool error (result.isError=true); допустимое значение принимается (result.isError=false).")
    @Tag("negative")
    @Tag("case-MCP-028")
    @Tag("jira")
    @Tag("confluence")
    void shouldRejectLimitOutsidePublishedBounds(String toolName,
                                                 String queryField,
                                                 String query,
                                                 McpClient client) {
        Allure.parameter("MCP tool", toolName);
        Allure.parameter("Поле запроса", queryField == null ? "отсутствует" : queryField);
        Allure.parameter("Минимальный допустимый limit", McpTestData.MIN_LIMIT);
        Allure.parameter("Максимальный допустимый limit", McpTestData.MAX_LIMIT);
        Allure.parameter("Контрольный допустимый limit", McpTestData.DEFAULT_LIMIT);

        assertLimitRejected(client, toolName, queryField, query, McpTestData.INVALID_LIMIT_BELOW_MIN);
        assertLimitRejected(client, toolName, queryField, query, McpTestData.INVALID_LIMIT_ABOVE_MAX);

        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        if (queryField != null) {
            arguments.put(queryField, query);
        }
        arguments.put("limit", McpTestData.DEFAULT_LIMIT);

        JsonNode response = Allure.step(
                "Вызвать " + toolName + " с допустимым limit=" + McpTestData.DEFAULT_LIMIT,
                () -> callTool(client, toolName, arguments));
        assertSuccessfulJsonRpc(response);
        assertThat(response.path("result").path("isError").asBoolean())
                .as("%s должен принимать допустимый limit=%d", toolName, McpTestData.DEFAULT_LIMIT)
                .isFalse();
    }

    private static Stream<Arguments> limitBoundaryCases() {
        return Stream.of(
                Arguments.of("jira_search", "jql", JiraTestData.SEARCH_JQL),
                Arguments.of("confluence_search", "cql", ConfluenceTestData.SEARCH_CQL),
                Arguments.of("confluence_list_spaces", null, null)
        );
    }

    @Test
    @Feature("Confluence Page Updates")
    @Story("CONF-019 — Обновление страницы без полей для изменения")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("CONF-019: confluence_update_page отклоняет запрос только с page_id=" + ConfluenceTestData.PAGE_ID + " без title и content")
    @Description("Предусловия: В тестовом Confluence-окружении существует страница с ID=" + ConfluenceTestData.PAGE_ID + ".\nШаги: вызвать confluence_update_page с единственным аргументом page_id=" + ConfluenceTestData.PAGE_ID + "; не передавать title и content.\nОжидаемый результат: вызов возвращает MCP tool error с диагностикой отсутствия полей для изменения; запрос не должен запускать обновление страницы.")
    @Tag("negative")
    @Tag("case-CONF-019")
    @Tag("confluence")
    void shouldRequireTitleOrContentForConfluenceUpdate(McpClient client) {
        Allure.parameter("MCP tool", "confluence_update_page");
        Allure.parameter("Переданные поля", "page_id");
        Allure.parameter("Отсутствующие поля", "title; content");
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("page_id", ConfluenceTestData.PAGE_ID);

        JsonNode response = Allure.step("Вызвать confluence_update_page только с page_id=" + ConfluenceTestData.PAGE_ID + ", без title и content",
                () -> callTool(client, "confluence_update_page", arguments));
        assertToolError(response, "confluence_update_page с page_id=" + ConfluenceTestData.PAGE_ID + " без title и content");
    
    
    }

    @Step("Отправить JSON-RPC request: method={method}")
    private static JsonNode sendRequest(McpClient client, String method, ObjectNode params) {
        return client.request(method, params, REQUEST_TIMEOUT);
    }

    @Step("Вызвать MCP-инструмент через tools/call: name={name}")
    private static JsonNode callTool(McpClient client, String name, ObjectNode arguments) {
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments);
        return client.requestStateless(
                McpTestData.TOOLS_CALL_METHOD,
                params,
                McpTestData.STATELESS_PROTOCOL_VERSION,
                OBJECT_MAPPER.createObjectNode(),
                null,
                REQUEST_TIMEOUT
        );
    }

    @Step("Получить tools/list в stateless-режиме и проверить структуру ответа")
    private static JsonNode getStatelessTools(McpClient client) {
        JsonNode response = client.listToolsStateless(
                McpTestData.STATELESS_PROTOCOL_VERSION,
                REQUEST_TIMEOUT
        );
        assertSuccessfulJsonRpc(response);
        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray()).as("tools/list при protocolVersion=%s должен вернуть массив инструментов, фактически: %s", McpTestData.STATELESS_PROTOCOL_VERSION, tools.getNodeType()).isTrue();
        return tools;
    }

    @Step("Проверить отклонение limit={limit} для инструмента {toolName}")
    private static void assertLimitRejected(McpClient client,
                                            String toolName,
                                            String queryField,
                                            String query,
                                            int limit) {
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        if (queryField != null) {
            arguments.put(queryField, query);
        }
        arguments.put("limit", limit);

        JsonNode response = callTool(client, toolName, arguments);
        assertToolError(response, toolName + " с limit=" + limit
                + " (допустимый диапазон [" + McpTestData.MIN_LIMIT + ", " + McpTestData.MAX_LIMIT + "])");
    }

    @Step("Проверить успешный результат MCP-инструмента: {context}")
    private static void assertSuccessfulToolCall(JsonNode response, String context) {
        assertSuccessfulJsonRpc(response);
        assertThat(response.path("result").path("isError").asBoolean())
                .as("%s должен завершиться без MCP tool error (result.isError=false)", context)
                .isFalse();
    }

    @Step("Проверить структуру успешного JSON-RPC response")
    private static void assertSuccessfulJsonRpc(JsonNode response) {
        assertThat(response.path("jsonrpc").asText())
                .as("Ответ должен соответствовать JSON-RPC версии 2.0")
                .isEqualTo(McpTestData.JSON_RPC_VERSION);
        assertThat(response.has("id"))
                .as("Ответ на запрос должен содержать исходный request id")
                .isTrue();
        assertThat(response.has("error"))
                .as("Успешный JSON-RPC ответ не должен содержать поле error")
                .isFalse();
        assertThat(response.path("result").isObject())
                .as("Успешный JSON-RPC ответ должен содержать объект result")
                .isTrue();
    }

        private static void assertToolError(JsonNode response) {
        assertToolError(response, "MCP-инструмент");
    }

    @Step("Проверить MCP tool error для сценария: {context}")
    private static void assertToolError(JsonNode response, String context) {
        assertSuccessfulJsonRpc(response);
        JsonNode result = response.path("result");
        assertThat(result.path("isError").asBoolean())
                .as("%s должен вернуть isError=true", context)
                .isTrue();
        assertThat(result.path("content").isArray())
                .as("%s должен вернуть массив content", context)
                .isTrue();
        assertThat(result.path("content").size())
                .as("%s должен вернуть диагностическое сообщение", context)
                .isGreaterThan(0);
        assertThat(result.path("content").get(0).path("type").asText())
                .as("%s: элемент content должен иметь type=text", context)
                .isEqualTo("text");
        assertThat(result.path("content").get(0).path("text").asText())
                .as("%s: текст ошибки должен начинаться с «Ошибка: »", context)
                .startsWith("Ошибка: ");
        assertThat(result.has("error"))
                .as("%s: ошибка должна находиться в result, а не в JSON-RPC error", context)
                .isFalse();
    }

    @Step("Проверить annotations инструмента {toolName}")
    private static void assertAnnotations(String toolName, JsonNode annotations, ToolContract expected) {
        assertThat(annotations.path("readOnlyHint").isBoolean()).as("%s: readOnlyHint должен быть boolean", toolName).isTrue();
        assertThat(annotations.path("destructiveHint").isBoolean()).as("%s: destructiveHint должен быть boolean", toolName).isTrue();
        assertThat(annotations.path("idempotentHint").isBoolean()).as("%s: idempotentHint должен быть boolean", toolName).isTrue();
        assertThat(annotations.path("openWorldHint").isBoolean()).as("%s: openWorldHint должен быть boolean", toolName).isTrue();
        assertThat(annotations.path("readOnlyHint").asBoolean()).as("%s: readOnlyHint должен быть %s", toolName, expected.isReadOnly())
                .isEqualTo(expected.isReadOnly());
        assertThat(annotations.path("destructiveHint").asBoolean()).as("%s: destructiveHint должен быть %s", toolName, expected.isDestructive())
                .isEqualTo(expected.isDestructive());
        assertThat(annotations.path("idempotentHint").asBoolean()).as("%s: idempotentHint должен быть %s", toolName, expected.isIdempotent())
                .isEqualTo(expected.isIdempotent());
        assertThat(annotations.path("openWorldHint").asBoolean()).as("%s: openWorldHint должен быть true", toolName).isTrue();
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

    /**
     * Ожидаемое описание публичного контракта MCP-инструмента.
     */
    private static final class ToolContract {

        private final Map<String, String> propertyTypes;
        private final Set<String> required;
        private final boolean readOnly;
        private final boolean destructive;
        private final boolean idempotent;

        private ToolContract(Map<String, String> propertyTypes,
                             Set<String> required,
                             boolean readOnly,
                             boolean destructive,
                             boolean idempotent) {
            this.propertyTypes = Map.copyOf(propertyTypes);
            this.required = Set.copyOf(required);
            this.readOnly = readOnly;
            this.destructive = destructive;
            this.idempotent = idempotent;
        }

        private Map<String, String> getPropertyTypes() {
            return propertyTypes;
        }

        private Set<String> getRequired() {
            return required;
        }

        private boolean isReadOnly() {
            return readOnly;
        }

        private boolean isDestructive() {
            return destructive;
        }

        private boolean isIdempotent() {
            return idempotent;
        }
    }
}
