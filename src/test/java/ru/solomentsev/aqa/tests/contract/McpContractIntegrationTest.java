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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ru.solomentsev.aqa.mcp.McpClient;
import ru.solomentsev.aqa.support.junit.McpServerTest;
import ru.solomentsev.aqa.testdata.ConfluenceTestData;
import ru.solomentsev.aqa.testdata.JiraTestData;
import ru.solomentsev.aqa.testdata.McpContractTestData;
import ru.solomentsev.aqa.testdata.McpTestData;

import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThat;
import static ru.solomentsev.aqa.support.mcp.McpContractTestSupport.callTool;
import static ru.solomentsev.aqa.support.mcp.McpContractTestSupport.fieldNames;
import static ru.solomentsev.aqa.support.mcp.McpContractTestSupport.requestStatelessTools;
import static ru.solomentsev.aqa.mcp.McpClientTestActions.sendRequest;
import static ru.solomentsev.aqa.support.mcp.McpContractTestSupport.textValues;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, McpContractTestData.ToolContract> EXPECTED_TOOLS = McpContractTestData.getExpectedTools();

    @Test
    @Feature("Tools Discovery")
    @Story("MCP-005 — Получение списка инструментов")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("MCP-005: tools/list возвращает массив инструментов")
    @Description("Предусловия: тестовый MCP-сервер запущен; доступен stateless tools/list с обязательными metadata.\nШаги: отправить tools/list с обязательными stateless metadata; проверить JSON-RPC response и поле result.tools.\nОжидаемый результат: response не содержит error; result является объектом; result.tools является массивом.")
    @Tag("case-MCP-005")
    @Tag("smoke")
    void shouldReturnToolsList(McpClient client) {
        Allure.parameter("MCP protocolVersion", McpTestData.getStatelessProtocolVersion());
        JsonNode response = requestStatelessTools(client);
        assertThat(response.path("jsonrpc").asText())
                .as("tools/list должен вернуть JSON-RPC 2.0 response")
                .isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(response.has("id"))
                .as("tools/list response должен содержать request id")
                .isTrue();
        assertThat(response.has("error"))
                .as("Успешный tools/list не должен содержать JSON-RPC error")
                .isFalse();
        assertThat(response.path("result").isObject())
                .as("Успешный tools/list должен вернуть объект result")
                .isTrue();
        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray())
                .as("tools/list должен вернуть массив tools")
                .isTrue();
    }

    @Test
    @Feature("Tools Discovery")
    @Story("MCP-006 — Проверка состава инструментов")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("MCP-006: Состав tools/list совпадает с контрактом")
    @Description("Предусловия: MCP-сервер отвечает на stateless tools/list.\nШаги: извлечь name каждого элемента result.tools; сравнить количество и полный набор имён с эталонным публичным контрактом.\nОжидаемый результат: количество и набор уникальных имён полностью совпадают с публичным контрактом.")
    @Tag("case-MCP-006")
    @Tag("smoke")
    void shouldExposeExpectedToolNames(McpClient client) {
        Allure.parameter("Ожидаемое количество инструментов", EXPECTED_TOOLS.size());
        JsonNode response = requestStatelessTools(client);
        assertThat(response.path("jsonrpc").asText())
                .as("tools/list должен вернуть JSON-RPC 2.0 response")
                .isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(response.has("id"))
                .as("tools/list response должен содержать request id")
                .isTrue();
        assertThat(response.has("error"))
                .as("Успешный tools/list не должен содержать JSON-RPC error")
                .isFalse();
        assertThat(response.path("result").isObject())
                .as("Успешный tools/list должен вернуть объект result")
                .isTrue();
        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray())
                .as("tools/list должен вернуть массив tools")
                .isTrue();
        Set<String> actualNames = new HashSet<>();
        for (JsonNode tool : tools) {
            assertThat(tool.has("name"))
                    .as("Каждый инструмент должен содержать поле name")
                    .isTrue();
            assertThat(tool.path("name").isTextual())
                    .as("Имя каждого инструмента должно быть строкой")
                    .isTrue();
            assertThat(tool.path("name").asText())
                    .as("Имя инструмента не должно быть пустым")
                    .isNotBlank();
            actualNames.add(tool.path("name").asText());
        }
        assertThat(tools.size()).as("Ожидалось %d инструментов по контракту, фактически получено %d", EXPECTED_TOOLS.size(), tools.size()).isEqualTo(EXPECTED_TOOLS.size());
        assertThat(actualNames).as("Имена tools должны совпадать с контрактом; фактически получены: %s", actualNames).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS.keySet());

    }

    @Test
    @Feature("Tools Discovery")
    @Story("MCP-007 — Проверка описаний инструментов")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-007: У каждого инструмента есть непустой description")
    @Description("Предусловия: MCP-сервер вернул массив инструментов через tools/list.\nШаги: для каждого элемента result.tools прочитать name и description.\nОжидаемый результат: у каждого инструмента description является непустой строкой; при нарушении сообщение указывает имя конкретного инструмента.")
    @Tag("case-MCP-007")
    void shouldExposeNonEmptyToolDescriptions(McpClient client) {
        Allure.parameter("Ожидаемое количество инструментов", EXPECTED_TOOLS.size());
        JsonNode response = requestStatelessTools(client);
        assertThat(response.path("jsonrpc").asText())
                .as("tools/list должен вернуть JSON-RPC 2.0 response")
                .isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(response.has("id"))
                .as("tools/list response должен содержать request id")
                .isTrue();
        assertThat(response.has("error"))
                .as("Успешный tools/list не должен содержать JSON-RPC error")
                .isFalse();
        assertThat(response.path("result").isObject())
                .as("Успешный tools/list должен вернуть объект result")
                .isTrue();
        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray())
                .as("tools/list должен вернуть массив tools")
                .isTrue();
        for (JsonNode tool : tools) {
            String name = tool.path("name").asText();
            assertThat(tool.has("description"))
                    .as("Инструмент %s должен содержать description", name)
                    .isTrue();
            assertThat(tool.path("description").isTextual())
                    .as("Описание инструмента %s должно быть строкой", name)
                    .isTrue();
            assertThat(tool.path("description").asText())
                    .as("Инструмент %s должен содержать непустое описание", name)
                    .isNotBlank();
        }

    }

    @Test
    @Feature("Tool Schemas")
    @Story("MCP-008 — Проверка JSON Schema и annotations инструментов")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-008: JSON Schema и annotations инструментов соответствуют контракту")
    @Description("Предусловия: MCP-сервер вернул инструменты через stateless tools/list; в тесте задан ожидаемый публичный контракт.\nШаги: для каждого инструмента сравнить inputSchema.type, properties, required, additionalProperties, типы и ограничения аргументов; затем проверить readOnlyHint, destructiveHint, idempotentHint и openWorldHint.\nОжидаемый результат: набор инструментов, схемы аргументов и все четыре annotation-флага каждого инструмента совпадают с ожидаемым контрактом; сообщение об ошибке указывает имя инструмента и проверяемое поле.")
    @Tag("case-MCP-008")
    @Tag("jira")
    @Tag("confluence")
    void shouldExposeExactToolSchemasAndAnnotations(McpClient client) {
        Allure.parameter("Ожидаемое количество инструментов", EXPECTED_TOOLS.size());
        JsonNode response = requestStatelessTools(client);
        assertThat(response.path("jsonrpc").asText())
                .as("tools/list должен вернуть JSON-RPC 2.0 response")
                .isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(response.has("id"))
                .as("tools/list response должен содержать request id")
                .isTrue();
        assertThat(response.has("error"))
                .as("Успешный tools/list не должен содержать JSON-RPC error")
                .isFalse();
        assertThat(response.path("result").isObject())
                .as("Успешный tools/list должен вернуть объект result")
                .isTrue();
        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray())
                .as("tools/list должен вернуть массив tools")
                .isTrue();
        for (JsonNode tool : tools) {
            assertThat(tool.has("name"))
                    .as("Каждый инструмент должен содержать поле name")
                    .isTrue();
            assertThat(tool.path("name").isTextual())
                    .as("Имя каждого инструмента должно быть строкой")
                    .isTrue();
            String name = tool.path("name").asText();
            assertThat(name)
                    .as("Имя инструмента не должно быть пустым")
                    .isNotBlank();
            McpContractTestData.ToolContract expected = EXPECTED_TOOLS.get(name);
            assertThat(expected).as("Инструмент %s должен быть объявлен в ожидаемом контракте", name).isNotNull();

            assertThat(tool.has("inputSchema"))
                    .as("%s: inputSchema должен присутствовать", name)
                    .isTrue();
            JsonNode schema = tool.path("inputSchema");
            assertThat(schema.isObject())
                    .as("%s: inputSchema должен быть объектом", name)
                    .isTrue();
            assertThat(schema.has("type"))
                    .as("%s: inputSchema.type должен присутствовать", name)
                    .isTrue();
            assertThat(schema.path("type").isTextual())
                    .as("%s: inputSchema.type должен быть строкой", name)
                    .isTrue();
            assertThat(schema.path("type").asText())
                    .as("%s: корень inputSchema должен иметь type=object", name)
                    .isEqualTo("object");
            assertThat(schema.has("additionalProperties"))
                    .as("%s: additionalProperties должен присутствовать", name)
                    .isTrue();
            assertThat(schema.path("additionalProperties").isBoolean())
                    .as("%s: additionalProperties должен иметь boolean type", name)
                    .isTrue();
            assertThat(schema.path("additionalProperties").asBoolean())
                    .as("%s: additionalProperties должен быть false", name)
                    .isFalse();
            assertThat(schema.path("properties").isObject()).as("%s: inputSchema должен содержать объект properties", name).isTrue();
            assertThat(fieldNames(schema.path("properties"))).as("%s: набор аргументов должен совпадать с контрактом", name)
                    .containsExactlyInAnyOrderElementsOf(expected.getPropertyTypes().keySet());

            if (expected.getRequired().isEmpty()) {
                assertThat(schema.has("required"))
                        .as("%s: поле required не должно присутствовать при пустом ожидаемом списке", name)
                        .isFalse();
            } else {
                assertThat(schema.has("required"))
                        .as("%s: поле required должно присутствовать", name)
                        .isTrue();
                assertThat(schema.path("required").isArray())
                        .as("%s: поле required должно быть массивом", name)
                        .isTrue();
                for (JsonNode requiredValue : schema.path("required")) {
                    assertThat(requiredValue.isTextual())
                            .as("%s: каждый элемент required должен быть строкой", name)
                            .isTrue();
                }
                assertThat(textValues(schema.path("required")))
                        .as("%s: список required должен совпадать с контрактом", name)
                        .containsExactlyInAnyOrderElementsOf(expected.getRequired());
            }

            for (Map.Entry<String, String> property : expected.getPropertyTypes().entrySet()) {
                JsonNode definition = schema.path("properties").path(property.getKey());
                assertThat(definition.isObject())
                        .as("%s.%s: определение аргумента должно быть объектом", name, property.getKey())
                        .isTrue();
                assertThat(definition.has("type"))
                        .as("%s.%s: type должен присутствовать", name, property.getKey())
                        .isTrue();
                assertThat(definition.path("type").isTextual())
                        .as("%s.%s: type должен быть строкой", name, property.getKey())
                        .isTrue();
                assertThat(definition.path("type").asText())
                        .as("%s.%s: ожидаемый type=%s", name, property.getKey(), property.getValue())
                        .isEqualTo(property.getValue());
                if ("string".equals(property.getValue())) {
                    assertThat(definition.has("minLength"))
                            .as("%s.%s: minLength должен присутствовать", name, property.getKey())
                            .isTrue();
                    assertThat(definition.path("minLength").isIntegralNumber())
                            .as("%s.%s: minLength должен быть целым числом", name, property.getKey())
                            .isTrue();
                    assertThat(definition.path("minLength").asInt())
                            .as("%s.%s: ожидаемый minLength=%d", name, property.getKey(), McpTestData.getMinStringLength())
                            .isEqualTo(McpTestData.getMinStringLength());
                }
                if ("integer".equals(property.getValue())) {
                    assertThat(definition.has("minimum"))
                            .as("%s.%s: minimum должен присутствовать", name, property.getKey())
                            .isTrue();
                    assertThat(definition.path("minimum").isIntegralNumber())
                            .as("%s.%s: minimum должен быть целым числом", name, property.getKey())
                            .isTrue();
                    assertThat(definition.path("minimum").asInt())
                            .as("%s.%s: ожидаемый minimum=%d", name, property.getKey(), McpTestData.getMinLimit())
                            .isEqualTo(McpTestData.getMinLimit());
                    if ("limit".equals(property.getKey())) {
                        assertThat(definition.has("maximum"))
                                .as("%s.%s: maximum должен присутствовать", name, property.getKey())
                                .isTrue();
                        assertThat(definition.path("maximum").isIntegralNumber())
                                .as("%s.%s: maximum должен быть целым числом", name, property.getKey())
                                .isTrue();
                        assertThat(definition.path("maximum").asInt())
                                .as("%s.%s: ожидаемый maximum=%d", name, property.getKey(), McpTestData.getMaxLimit())
                                .isEqualTo(McpTestData.getMaxLimit());
                    }
                }
            }
            JsonNode annotations = tool.path("annotations");
            assertThat(tool.has("annotations")).as("%s: annotations должны присутствовать", name).isTrue();
            assertThat(annotations.isObject()).as("%s: annotations должны быть объектом", name).isTrue();
            assertThat(annotations.has("readOnlyHint")).as("%s: readOnlyHint должен присутствовать", name).isTrue();
            assertThat(annotations.path("readOnlyHint").isBoolean()).as("%s: readOnlyHint должен иметь boolean type", name).isTrue();
            assertThat(annotations.path("readOnlyHint").asBoolean()).as("%s: readOnlyHint должен соответствовать контракту", name).isEqualTo(expected.isReadOnly());
            assertThat(annotations.has("destructiveHint")).as("%s: destructiveHint должен присутствовать", name).isTrue();
            assertThat(annotations.path("destructiveHint").isBoolean()).as("%s: destructiveHint должен иметь boolean type", name).isTrue();
            assertThat(annotations.path("destructiveHint").asBoolean()).as("%s: destructiveHint должен соответствовать контракту", name).isEqualTo(expected.isDestructive());
            assertThat(annotations.has("idempotentHint")).as("%s: idempotentHint должен присутствовать", name).isTrue();
            assertThat(annotations.path("idempotentHint").isBoolean()).as("%s: idempotentHint должен иметь boolean type", name).isTrue();
            assertThat(annotations.path("idempotentHint").asBoolean()).as("%s: idempotentHint должен соответствовать контракту", name).isEqualTo(expected.isIdempotent());
            assertThat(annotations.has("openWorldHint")).as("%s: openWorldHint должен присутствовать", name).isTrue();
            assertThat(annotations.path("openWorldHint").isBoolean()).as("%s: openWorldHint должен иметь boolean type", name).isTrue();
            assertThat(annotations.path("openWorldHint").asBoolean())
                    .as("%s: openWorldHint должен соответствовать контракту", name)
                    .isEqualTo(expected.isOpenWorld());
        }

    }

    @ParameterizedTest(name = "MCP-023: отклонение stateless-запроса без {0}")
    @MethodSource("ru.solomentsev.aqa.testdata.McpContractTestData#getMissingStatelessMetadataCases")
    @Feature("Stateless Protocol")
    @Story("MCP-023 — Проверка обязательных stateless metadata")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-023: Stateless-запрос без обязательного metadata отклоняется")
    @Description("Предусловия: MCP STDIO-процесс запущен и прошёл classic initialize.\nШаги: отправить stateless-запрос без одного из обязательных metadata-полей; затем выполнить корректный tools/list.\nОжидаемый результат: запрос без обязательного metadata возвращает JSON-RPC error code=INVALID_PARAMS; контрольный tools/list с обязательными metadata выполняется успешно и возвращает массив tools.")
    @Tag("negative")
    @Tag("case-MCP-023")
    void shouldRejectMissingRequiredStatelessMetadata(String missingField,
                                                      String method,
                                                      McpClient client) {
        Allure.parameter("MCP method", method);
        Allure.parameter("Отсутствующее metadata поле", missingField);
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getJsonRpcInvalidParams());

        ObjectNode params = null;
        if ("clientCapabilities".equals(missingField)) {
            params = OBJECT_MAPPER.createObjectNode();
            params.putObject("_meta")
                    .put("io.modelcontextprotocol/protocolVersion", McpTestData.getStatelessProtocolVersion());
        } else if ("protocolVersion".equals(missingField)) {
            params = OBJECT_MAPPER.createObjectNode();
            params.putObject("_meta")
                    .putObject("io.modelcontextprotocol/clientCapabilities");
        }

        ObjectNode requestParams = params;
        JsonNode invalidResponse = sendRequest(client, method, requestParams);
        assertThat(invalidResponse.has("error"))
                .as("%s без обязательного %s должен вернуть JSON-RPC error", method, missingField)
                .isTrue();
        assertThat(invalidResponse.path("error").isObject())
                .as("JSON-RPC error должен быть объектом")
                .isTrue();
        assertThat(invalidResponse.path("error").has("code"))
                .as("JSON-RPC error должен содержать code")
                .isTrue();
        assertThat(invalidResponse.path("error").path("code").isIntegralNumber())
                .as("JSON-RPC error.code должен быть целым числом")
                .isTrue();
        assertThat(invalidResponse.path("error").path("code").asInt())
                .as("%s без обязательного %s должен вернуть JSON-RPC error code=%d",
                        method, missingField, McpTestData.getJsonRpcInvalidParams())
                .isEqualTo(McpTestData.getJsonRpcInvalidParams());

        JsonNode validResponse = Allure.step(
                "Повторить tools/list с обязательными metadata и protocolVersion=" + McpTestData.getStatelessProtocolVersion(),
                () -> client.listToolsStateless(McpTestData.getStatelessProtocolVersion(), McpTestData.getRequestTimeout()));
        assertThat(validResponse.path("jsonrpc").asText())
                .as("Контрольный tools/list: response должен соответствовать JSON-RPC 2.0")
                .isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(validResponse.has("id"))
                .as("Контрольный tools/list: response должен содержать request id")
                .isTrue();
        assertThat(validResponse.has("error"))
                .as("Контрольный tools/list: успешный response не должен содержать error")
                .isFalse();
        assertThat(validResponse.path("result").isObject())
                .as("Контрольный tools/list: response должен содержать объект result")
                .isTrue();
        assertThat(validResponse.path("result").path("tools").isArray())
                .as("Контрольный stateless tools/list должен вернуть массив result.tools после отклонения некорректного запроса")
                .isTrue();
    }

    @Test
    @Feature("Stateless Protocol")
    @Story("MCP-022 — Отклонение неподдерживаемой stateless protocolVersion")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-022: server/discover отклоняет неподдерживаемую protocolVersion")
    @Description("Предусловия: MCP STDIO-процесс запущен и готов принимать JSON-RPC запросы.\nШаги: вызвать server/discover с неподдерживаемой protocolVersion и непустым clientCapabilities.\nОжидаемый результат: сервер возвращает JSON-RPC error code=-32022; error.data.supportedVersions содержит поддерживаемую версию протокола.")
    @Tag("negative")
    @Tag("case-MCP-022")
    void shouldRejectUnsupportedStatelessProtocolVersion(McpClient client) {
        Allure.parameter("Переданный protocolVersion", McpTestData.getUnsupportedProtocolVersion());
        Allure.parameter("Поддерживаемый protocolVersion", McpTestData.getStatelessProtocolVersion());
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        ObjectNode metadata = params.putObject("_meta");
        metadata.put("io.modelcontextprotocol/protocolVersion", McpTestData.getUnsupportedProtocolVersion());
        metadata.putObject("io.modelcontextprotocol/clientCapabilities");

        JsonNode response = sendRequest(client, McpTestData.getServerDiscoverMethod(), params);

        assertThat(response.has("error"))
                .as("Неподдерживаемая protocolVersion должна вернуть JSON-RPC error")
                .isTrue();
        assertThat(response.path("error").path("code").isIntegralNumber())
                .as("JSON-RPC error.code должен быть целым числом")
                .isTrue();
        assertThat(response.path("error").path("code").asInt())
                .as("Неподдерживаемая protocolVersion должна вернуть error code -32022")
                .isEqualTo(McpTestData.getMcpUnsupportedProtocolVersion());
        assertThat(textValues(response.path("error").path("data").path("supportedVersions"))).as("В ошибке должны быть перечислены поддерживаемые версии протокола")
                .containsExactly(McpTestData.getStatelessProtocolVersion());

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
        Allure.parameter("MCP method", McpTestData.getToolsCallMethod());
        Allure.parameter("Tool name", "aqa_unknown_tool");
        JsonNode response = callTool(client, "aqa_unknown_tool", OBJECT_MAPPER.createObjectNode());

        JsonNode toolErrorResult = response.path("result");
        assertThat(response.path("jsonrpc").asText()).as("Проверка MCP tool error: response должен соответствовать JSON-RPC 2.0").isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(response.has("id")).as("Проверка MCP tool error: response должен содержать request id").isTrue();
        assertThat(response.has("error")).as("Проверка MCP tool error: MCP tool error не должен быть JSON-RPC error").isFalse();
        assertThat(toolErrorResult.isObject()).as("Проверка MCP tool error: response должен содержать объект result").isTrue();
        assertThat(toolErrorResult.path("isError").isBoolean()).as("Проверка MCP tool error: result.isError должен иметь boolean type").isTrue();
        assertThat(toolErrorResult.path("isError").asBoolean()).as("Проверка MCP tool error: result.isError должен быть true").isTrue();
        assertThat(toolErrorResult.path("content").isArray()).as("Проверка MCP tool error: result.content должен быть массивом").isTrue();
        assertThat(toolErrorResult.path("content").size()).as("Проверка MCP tool error: result.content должен содержать диагностическое сообщение").isGreaterThan(0);
        assertThat(toolErrorResult.path("content").path(0).path("type").asText()).as("Проверка MCP tool error: content[0].type должен быть text").isEqualTo(McpTestData.getTextContentType());
        assertThat(toolErrorResult.path("content").path(0).path("text").asText()).as("Проверка MCP tool error: текст диагностики должен начинаться с «Ошибка: »").startsWith("Ошибка: ");

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
        JsonNode response = callTool(client, "jira_get_issue", OBJECT_MAPPER.createObjectNode());

        JsonNode toolErrorResult = response.path("result");
        assertThat(response.path("jsonrpc").asText()).as("Проверка MCP tool error: response должен соответствовать JSON-RPC 2.0").isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(response.has("id")).as("Проверка MCP tool error: response должен содержать request id").isTrue();
        assertThat(response.has("error")).as("Проверка MCP tool error: MCP tool error не должен быть JSON-RPC error").isFalse();
        assertThat(toolErrorResult.isObject()).as("Проверка MCP tool error: response должен содержать объект result").isTrue();
        assertThat(toolErrorResult.path("isError").isBoolean()).as("Проверка MCP tool error: result.isError должен иметь boolean type").isTrue();
        assertThat(toolErrorResult.path("isError").asBoolean()).as("Проверка MCP tool error: result.isError должен быть true").isTrue();
        assertThat(toolErrorResult.path("content").isArray()).as("Проверка MCP tool error: result.content должен быть массивом").isTrue();
        assertThat(toolErrorResult.path("content").size()).as("Проверка MCP tool error: result.content должен содержать диагностическое сообщение").isGreaterThan(0);
        assertThat(toolErrorResult.path("content").path(0).path("type").asText()).as("Проверка MCP tool error: content[0].type должен быть text").isEqualTo(McpTestData.getTextContentType());
        assertThat(toolErrorResult.path("content").path(0).path("text").asText()).as("Проверка MCP tool error: текст диагностики должен начинаться с «Ошибка: »").startsWith("Ошибка: ");

    }

    @ParameterizedTest(name = "MCP-034: jira_get_issue отклоняет issue_key={0}")
    @MethodSource("ru.solomentsev.aqa.testdata.McpContractTestData#getRequiredArgumentCases")
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

        JsonNode response = callTool(client, "jira_get_issue", arguments);
        JsonNode toolErrorResult = response.path("result");
        assertThat(response.path("jsonrpc").asText()).as("Проверка MCP tool error: response должен соответствовать JSON-RPC 2.0").isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(response.has("id")).as("Проверка MCP tool error: response должен содержать request id").isTrue();
        assertThat(response.has("error")).as("Проверка MCP tool error: MCP tool error не должен быть JSON-RPC error").isFalse();
        assertThat(toolErrorResult.isObject()).as("Проверка MCP tool error: response должен содержать объект result").isTrue();
        assertThat(toolErrorResult.path("isError").isBoolean()).as("Проверка MCP tool error: result.isError должен иметь boolean type").isTrue();
        assertThat(toolErrorResult.path("isError").asBoolean()).as("Проверка MCP tool error: result.isError должен быть true").isTrue();
        assertThat(toolErrorResult.path("content").isArray()).as("Проверка MCP tool error: result.content должен быть массивом").isTrue();
        assertThat(toolErrorResult.path("content").size()).as("Проверка MCP tool error: result.content должен содержать диагностическое сообщение").isGreaterThan(0);
        assertThat(toolErrorResult.path("content").path(0).path("type").asText()).as("Проверка MCP tool error: content[0].type должен быть text").isEqualTo(McpTestData.getTextContentType());
        assertThat(toolErrorResult.path("content").path(0).path("text").asText()).as("Проверка MCP tool error: текст диагностики должен начинаться с «Ошибка: »").startsWith("Ошибка: ");
    }

    @Test
    @Feature("Tool Validation")
    @Story("MCP-012 — Вызов инструмента с неверным типом аргумента")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-012: jira_get_issue отклоняет issue_key неверного JSON-типа")
    @Description("Предусловия: MCP-сервер запущен; схема jira_get_issue объявляет issue_key строковым обязательным аргументом.\nШаги: вызвать jira_get_issue с числовым issue_key.\nОжидаемый результат: вызов с аргументом неверного JSON-типа возвращает MCP tool error (result.isError=true); проверка не зависит от наличия конкретной задачи в Jira.")
    @Tag("negative")
    @Tag("case-MCP-012")
    @Tag("jira")
    void shouldRejectWrongToolArgumentType(McpClient client) {
        Allure.parameter("MCP tool", "jira_get_issue");
        Allure.parameter("Аргумент issue_key", JiraTestData.getInvalidIssueKeyNumber() + " (число вместо строки)");
        Allure.parameter("Ожидаемый тип issue_key", "string");
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("issue_key", JiraTestData.getInvalidIssueKeyNumber());
        JsonNode invalidResponse = callTool(client, "jira_get_issue", arguments);
        JsonNode toolErrorResult = invalidResponse.path("result");
        assertThat(invalidResponse.path("jsonrpc").asText()).as("Проверка MCP tool error: response должен соответствовать JSON-RPC 2.0").isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(invalidResponse.has("id")).as("Проверка MCP tool error: response должен содержать request id").isTrue();
        assertThat(invalidResponse.has("error")).as("Проверка MCP tool error: MCP tool error не должен быть JSON-RPC error").isFalse();
        assertThat(toolErrorResult.isObject()).as("Проверка MCP tool error: response должен содержать объект result").isTrue();
        assertThat(toolErrorResult.path("isError").isBoolean()).as("Проверка MCP tool error: result.isError должен иметь boolean type").isTrue();
        assertThat(toolErrorResult.path("isError").asBoolean()).as("Проверка MCP tool error: result.isError должен быть true").isTrue();
        assertThat(toolErrorResult.path("content").isArray()).as("Проверка MCP tool error: result.content должен быть массивом").isTrue();
        assertThat(toolErrorResult.path("content").size()).as("Проверка MCP tool error: result.content должен содержать диагностическое сообщение").isGreaterThan(0);
        assertThat(toolErrorResult.path("content").path(0).path("type").asText()).as("Проверка MCP tool error: content[0].type должен быть text").isEqualTo(McpTestData.getTextContentType());
        assertThat(toolErrorResult.path("content").path(0).path("text").asText()).as("Проверка MCP tool error: текст диагностики должен начинаться с «Ошибка: »").startsWith("Ошибка: ");

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
        arguments.put("issue_key", JiraTestData.getIssueKey());
        arguments.put("unexpected", "value");
        JsonNode invalidResponse = callTool(client, "jira_get_issue", arguments);
        JsonNode toolErrorResult = invalidResponse.path("result");
        assertThat(invalidResponse.path("jsonrpc").asText()).as("Проверка MCP tool error: response должен соответствовать JSON-RPC 2.0").isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(invalidResponse.has("id")).as("Проверка MCP tool error: response должен содержать request id").isTrue();
        assertThat(invalidResponse.has("error")).as("Проверка MCP tool error: MCP tool error не должен быть JSON-RPC error").isFalse();
        assertThat(toolErrorResult.isObject()).as("Проверка MCP tool error: response должен содержать объект result").isTrue();
        assertThat(toolErrorResult.path("isError").isBoolean()).as("Проверка MCP tool error: result.isError должен иметь boolean type").isTrue();
        assertThat(toolErrorResult.path("isError").asBoolean()).as("Проверка MCP tool error: result.isError должен быть true").isTrue();
        assertThat(toolErrorResult.path("content").isArray()).as("Проверка MCP tool error: result.content должен быть массивом").isTrue();
        assertThat(toolErrorResult.path("content").size()).as("Проверка MCP tool error: result.content должен содержать диагностическое сообщение").isGreaterThan(0);
        assertThat(toolErrorResult.path("content").path(0).path("type").asText()).as("Проверка MCP tool error: content[0].type должен быть text").isEqualTo(McpTestData.getTextContentType());
        assertThat(toolErrorResult.path("content").path(0).path("text").asText()).as("Проверка MCP tool error: текст диагностики должен начинаться с «Ошибка: »").startsWith("Ошибка: ");

    }

    @ParameterizedTest(name = "MCP-028: {0} проверяет границы limit")
    @MethodSource("ru.solomentsev.aqa.testdata.McpContractTestData#getLimitBoundaryCases")
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
        Allure.parameter("Минимальный допустимый limit", McpTestData.getMinLimit());
        Allure.parameter("Максимальный допустимый limit", McpTestData.getMaxLimit());
        Allure.parameter("Контрольный допустимый limit", McpTestData.getDefaultLimit());

        for (int invalidLimit : List.of(
                McpTestData.getInvalidLimitBelowMin(),
                McpTestData.getInvalidLimitAboveMax())) {
            ObjectNode invalidArguments = OBJECT_MAPPER.createObjectNode();
            if (queryField != null) {
                invalidArguments.put(queryField, query);
            }
            invalidArguments.put("limit", invalidLimit);
            Allure.parameter("Недопустимый limit", invalidLimit);
            JsonNode invalidResponse = callTool(client, toolName, invalidArguments);
            JsonNode invalidResult = invalidResponse.path("result");
            assertThat(invalidResponse.has("error"))
                    .as("%s с limit=%d не должен возвращать JSON-RPC error", toolName, invalidLimit)
                    .isFalse();
            assertThat(invalidResult.path("isError").isBoolean())
                    .as("%s: result.isError должен иметь boolean type", toolName)
                    .isTrue();
            assertThat(invalidResult.path("isError").asBoolean())
                    .as("%s должен отклонять limit=%d", toolName, invalidLimit)
                    .isTrue();
            assertThat(invalidResult.path("content").isArray())
                    .as("%s должен возвращать массив content при отклонении limit=%d", toolName, invalidLimit)
                    .isTrue();
            assertThat(invalidResult.path("content").size())
                    .as("%s должен возвращать диагностическое сообщение при limit=%d", toolName, invalidLimit)
                    .isGreaterThan(0);
        }

        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        if (queryField != null) {
            arguments.put(queryField, query);
        }
        arguments.put("limit", McpTestData.getDefaultLimit());

        Allure.parameter("Допустимый limit", McpTestData.getDefaultLimit());
        JsonNode response = callTool(client, toolName, arguments);
        assertThat(response.path("jsonrpc").asText())
                .as("Вызов MCP-инструмента: response должен соответствовать JSON-RPC 2.0")
                .isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(response.has("id"))
                .as("Вызов MCP-инструмента: response должен содержать request id")
                .isTrue();
        assertThat(response.has("error"))
                .as("Вызов MCP-инструмента: успешный response не должен содержать error")
                .isFalse();
        assertThat(response.path("result").isObject())
                .as("Вызов MCP-инструмента: response должен содержать объект result")
                .isTrue();
        assertThat(response.path("result").has("isError"))
                .as("%s должен возвращать result.isError", toolName)
                .isTrue();
        assertThat(response.path("result").path("isError").isBoolean())
                .as("%s: result.isError должен иметь boolean type", toolName)
                .isTrue();
        assertThat(response.path("result").path("isError").asBoolean())
                .as("%s должен принимать допустимый limit=%d", toolName, McpTestData.getDefaultLimit())
                .isFalse();
    }

    @Test
    @Feature("Confluence Page Updates")
    @Story("CONF-019 — Обновление страницы без полей для изменения")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("CONF-019: confluence_update_page отклоняет запрос без title и content")
    @Description("Предусловия: В тестовом Confluence-окружении существует тестовая страница.\nШаги: вызвать confluence_update_page только с page_id; не передавать title и content.\nОжидаемый результат: вызов возвращает MCP tool error с диагностикой отсутствия полей для изменения; запрос не должен запускать обновление страницы.")
    @Tag("negative")
    @Tag("case-CONF-019")
    @Tag("confluence")
    void shouldRequireTitleOrContentForConfluenceUpdate(McpClient client) {
        Allure.parameter("MCP tool", "confluence_update_page");
        Allure.parameter("Переданные поля", "page_id");
        Allure.parameter("Отсутствующие поля", "title; content");
        ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
        arguments.put("page_id", ConfluenceTestData.getPageId());

        JsonNode response = callTool(client, "confluence_update_page", arguments);
        JsonNode toolErrorResult = response.path("result");
        assertThat(response.path("jsonrpc").asText()).as("Проверка MCP tool error: response должен соответствовать JSON-RPC 2.0").isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(response.has("id")).as("Проверка MCP tool error: response должен содержать request id").isTrue();
        assertThat(response.has("error")).as("Проверка MCP tool error: MCP tool error не должен быть JSON-RPC error").isFalse();
        assertThat(toolErrorResult.isObject()).as("Проверка MCP tool error: response должен содержать объект result").isTrue();
        assertThat(toolErrorResult.path("isError").isBoolean()).as("Проверка MCP tool error: result.isError должен иметь boolean type").isTrue();
        assertThat(toolErrorResult.path("isError").asBoolean()).as("Проверка MCP tool error: result.isError должен быть true").isTrue();
        assertThat(toolErrorResult.path("content").isArray()).as("Проверка MCP tool error: result.content должен быть массивом").isTrue();
        assertThat(toolErrorResult.path("content").size()).as("Проверка MCP tool error: result.content должен содержать диагностическое сообщение").isGreaterThan(0);
        assertThat(toolErrorResult.path("content").path(0).path("type").asText()).as("Проверка MCP tool error: content[0].type должен быть text").isEqualTo(McpTestData.getTextContentType());
        assertThat(toolErrorResult.path("content").path(0).path("text").asText()).as("Проверка MCP tool error: текст диагностики должен начинаться с «Ошибка: »").startsWith("Ошибка: ");

    }

}
