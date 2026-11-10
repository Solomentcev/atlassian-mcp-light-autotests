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
import ru.solomentsev.aqa.junit.McpServerTest;
import ru.solomentsev.aqa.mcp.McpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
class McpContractIntegrationTest {

    private static final String STATELESS_PROTOCOL_VERSION = "2026-07-28";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Map<String, ToolContract> EXPECTED_TOOLS = expectedTools();

    @Test
    @Feature("Tools Discovery")
    @Story("MCP-005 — Получение списка инструментов")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("MCP-005: Получение списка инструментов")
    @Description("Предусловия: STDIO-процесс запущен; доступен stateless tools/list.\nШаги: вызвать `tools/list` с корректными stateless metadata.\nОжидаемый результат: возвращён успешный JSON-RPC result, содержащий массив tools.")
    @Tag("integration")
    @Tag("case-MCP-005")
    void shouldReturnToolsList(McpClient client) {
        Allure.step("Получение списка инструментов", () -> {
            JsonNode tools = getStatelessTools(client);
            assertThat(tools.isArray()).isTrue();
        });
    }

    @Test
    @Feature("Tools Discovery")
    @Story("MCP-006 — Проверка состава инструментов")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("MCP-006: Проверка состава инструментов")
    @Description("Предусловия: Получен результат `tools/list`.\nШаги: извлечь имена инструментов и сравнить с ожидаемым перечнем.\nОжидаемый результат: присутствуют все 13 ожидаемых инструментов; имена уникальны; неожиданные инструменты отсутствуют.")
    @Tag("integration")
    @Tag("case-MCP-006")
    void shouldExposeExpectedToolNames(McpClient client) {
        Allure.step("Проверка состава инструментов", () -> {
            JsonNode tools = getStatelessTools(client);
            Set<String> actualNames = new HashSet<>();
            for (JsonNode tool : tools) {
                actualNames.add(tool.path("name").asText());
            }
            assertThat(tools.size()).isEqualTo(EXPECTED_TOOLS.size());
            assertThat(actualNames).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOLS.keySet());
        });
    }

    @Test
    @Feature("Tools Discovery")
    @Story("MCP-007 — Проверка описаний инструментов")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-007: Проверка описаний инструментов")
    @Description("Предусловия: Получен результат `tools/list`.\nШаги: проверить description каждого инструмента.\nОжидаемый результат: каждый инструмент содержит непустое текстовое описание.")
    @Tag("integration")
    @Tag("case-MCP-007")
    void shouldExposeNonEmptyToolDescriptions(McpClient client) {
        Allure.step("Проверка описаний инструментов", () -> {
            JsonNode tools = getStatelessTools(client);
            for (JsonNode tool : tools) {
                assertThat(tool.path("description").asText())
                        .as("description of %s", tool.path("name").asText())
                        .isNotBlank();
            }
        });
    }

    @Test
    @Feature("Tool Schemas")
    @Story("MCP-008 — Проверка JSON Schema и annotations инструментов")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-008: Проверка JSON Schema и annotations инструментов")
    @Description("Предусловия: Получен результат `tools/list`.\nШаги: проверить type, properties, required, additionalProperties, ограничения JSON Schema и annotations каждого инструмента; сравнить результат с mcp-contract.md.\nОжидаемый результат: схемы и annotations всех 13 инструментов соответствуют опубликованному контракту.")
    @Tag("integration")
    @Tag("case-MCP-008")
    @Tag("jira")
    @Tag("confluence")
    void shouldExposeExactToolSchemasAndAnnotations(McpClient client) {
        Allure.step("Проверка JSON Schema и annotations инструментов", () -> {
            JsonNode tools = getStatelessTools(client);
            for (JsonNode tool : tools) {
                String name = tool.path("name").asText();
                ToolContract expected = EXPECTED_TOOLS.get(name);
                assertThat(expected).as("Unexpected tool: %s", name).isNotNull();

                JsonNode schema = tool.path("inputSchema");
                assertThat(schema.path("type").asText()).isEqualTo("object");
                assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
                assertThat(schema.path("properties").isObject()).isTrue();
                assertThat(fieldNames(schema.path("properties")))
                        .containsExactlyInAnyOrderElementsOf(expected.propertyTypes().keySet());

                if (expected.required().isEmpty()) {
                    assertThat(schema.has("required")).isFalse();
                } else {
                    assertThat(textValues(schema.path("required")))
                            .containsExactlyInAnyOrderElementsOf(expected.required());
                }

                for (Map.Entry<String, String> property : expected.propertyTypes().entrySet()) {
                    JsonNode definition = schema.path("properties").path(property.getKey());
                    assertThat(definition.path("type").asText())
                            .as("%s.%s type", name, property.getKey())
                            .isEqualTo(property.getValue());
                    if ("string".equals(property.getValue())) {
                        assertThat(definition.path("minLength").asInt())
                                .as("%s.%s minLength", name, property.getKey())
                                .isEqualTo(1);
                    }
                    if ("integer".equals(property.getValue())) {
                        assertThat(definition.path("minimum").asInt())
                                .as("%s.%s minimum", name, property.getKey())
                                .isEqualTo(1);
                        if ("limit".equals(property.getKey())) {
                            assertThat(definition.path("maximum").asInt())
                                    .as("%s.%s maximum", name, property.getKey())
                                    .isEqualTo(100);
                        }
                    }
                }
                assertAnnotations(tool.path("annotations"), expected);
            }
        });
    }

    @Test
    @Feature("Stateless Protocol")
    @Story("MCP-023 — Проверка обязательных stateless metadata")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-023: Проверка обязательных stateless metadata")
    @Description("Предусловия: STDIO-процесс запущен\nШаги: 1. Отправить `server/discover` без `_meta`. 2. Отправить stateless `tools/list` с `_meta`, содержащим protocolVersion, но без clientCapabilities. 3. Повторить с clientCapabilities, но без protocolVersion.\nОжидаемый результат: Для каждого запроса получен JSON-RPC error `-32602 Invalid params`; после негативных запросов корректный stateless-запрос обрабатывается штатно.")
    @Tag("negative")
    @Tag("case-MCP-023")
    void shouldRejectMissingRequiredStatelessMetadata(McpClient client) {
        Allure.step("Проверка обязательных stateless metadata", () -> {
            JsonNode missingMetadata = client.request("server/discover", null, REQUEST_TIMEOUT);
            assertThat(missingMetadata.path("error").path("code").asInt()).isEqualTo(-32602);

            ObjectNode missingCapabilitiesParams = OBJECT_MAPPER.createObjectNode();
            missingCapabilitiesParams.putObject("_meta")
                    .put("io.modelcontextprotocol/protocolVersion", STATELESS_PROTOCOL_VERSION);
            JsonNode missingCapabilities = client.request(
                    "tools/list", missingCapabilitiesParams, REQUEST_TIMEOUT);
            assertThat(missingCapabilities.path("error").path("code").asInt()).isEqualTo(-32602);

            ObjectNode missingVersionParams = OBJECT_MAPPER.createObjectNode();
            missingVersionParams.putObject("_meta")
                    .putObject("io.modelcontextprotocol/clientCapabilities");
            JsonNode missingVersion = client.request(
                    "tools/list", missingVersionParams, REQUEST_TIMEOUT);
            assertThat(missingVersion.path("error").path("code").asInt()).isEqualTo(-32602);

            assertSuccessfulJsonRpc(client.listToolsStateless(
                    STATELESS_PROTOCOL_VERSION, REQUEST_TIMEOUT));
        });
    }

    @Test
    @Feature("Stateless Tools")
    @Story("MCP-021 — Вызов tools/call в stateless-режиме")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-021: Вызов tools/call в stateless-режиме")
    @Description("Предусловия: STDIO-процесс запущен; initialize не отправлялся; доступен безопасный read-инструмент\nШаги: 1. Вызвать безопасный read-инструмент через `tools/call`, передав корректные stateless metadata.\nОжидаемый результат: Запрос выполняется без initialize; ответ содержит обычный MCP tool result, `resultType=complete` и serverInfo metadata.")
    @Tag("integration")
    @Tag("case-MCP-021")
    @Tag("confluence")
    void shouldReturnSuccessfulToolCallEnvelope(McpClient client) {
        Allure.step("Вызов tools/call в stateless-режиме", () -> {
            ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
            arguments.put("limit", 1);

            JsonNode response = callTool(client, "confluence_list_spaces", arguments);

            assertSuccessfulJsonRpc(response);
            JsonNode result = response.path("result");
            assertThat(result.path("isError").asBoolean()).isFalse();
            assertThat(result.path("content").isArray()).isTrue();
            assertThat(result.path("content").size()).isGreaterThan(0);
            assertThat(result.path("content").get(0).path("type").asText()).isEqualTo("text");
            assertThat(result.path("content").get(0).path("text").isTextual()).isTrue();
            assertThat(result.path("resultType").asText()).isEqualTo("complete");
            assertThat(result.path("_meta").path("io.modelcontextprotocol/serverInfo")
                    .path("name").asText()).isEqualTo("atlassian-lite");
        
        });
    }

    @Test
    @Feature("Stateless Protocol")
    @Story("MCP-022 — Отклонение неподдерживаемой stateless protocolVersion")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-022: Отклонение неподдерживаемой stateless protocolVersion")
    @Description("Предусловия: STDIO-процесс запущен\nШаги: 1. Отправить stateless-запрос с версией, отличной от `2026-07-28`.\nОжидаемый результат: Получен JSON-RPC error `-32022 Unsupported protocol version`; в `error.data.supportedVersions` присутствует `2026-07-28`.")
    @Tag("negative")
    @Tag("case-MCP-022")
    void shouldRejectUnsupportedStatelessProtocolVersion(McpClient client) {
        Allure.step("Отклонение неподдерживаемой stateless protocolVersion", () -> {
            ObjectNode params = OBJECT_MAPPER.createObjectNode();
            ObjectNode metadata = params.putObject("_meta");
            metadata.put("io.modelcontextprotocol/protocolVersion", "2099-01-01");
            metadata.putObject("io.modelcontextprotocol/clientCapabilities");

            JsonNode response = client.request(
                    "server/discover",
                    params,
                    REQUEST_TIMEOUT
            );

            assertThat(response.path("error").path("code").asInt()).isEqualTo(-32022);
            assertThat(textValues(response.path("error").path("data").path("supportedVersions")))
                    .containsExactly("2026-07-28");
        
        });
    }

    @Test
    @Feature("Tool Calls")
    @Story("MCP-010 — Вызов неизвестного инструмента")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-010: Вызов неизвестного инструмента")
    @Description("Предусловия: Инициализация завершена\nШаги: 1. Вызвать `tools/call` с неизвестным именем инструмента.\nОжидаемый результат: Сервер возвращает JSON-RPC response с `result.isError=true` и текстом ошибки в `result.content`; верхнеуровневый JSON-RPC `error` для ошибки dispatch не используется.")
    @Tag("negative")
    @Tag("case-MCP-010")
    void shouldReturnToolErrorForUnknownToolWithoutJsonRpcError(McpClient client) {
        Allure.step("Вызов неизвестного инструмента", () -> {
            JsonNode response = callTool(client, "aqa_unknown_tool", OBJECT_MAPPER.createObjectNode());

            assertToolError(response);
        
        });
    }

    @Test
    @Feature("Tool Validation")
    @Story("MCP-011 — Вызов инструмента без обязательного аргумента")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-011: Вызов инструмента без обязательного аргумента")
    @Description("Предусловия: Инициализация завершена\nШаги: 1. Вызвать инструмент без одного обязательного аргумента.\nОжидаемый результат: Возвращается JSON-RPC response с `result.isError=true`; `result.content[0].text` содержит сообщение об ошибке обязательного параметра.")
    @Tag("negative")
    @Tag("case-MCP-011")
    @Tag("jira")
    void shouldRejectMissingRequiredToolArgument(McpClient client) {
        Allure.step("Вызов инструмента без обязательного аргумента", () -> {
            JsonNode response = callTool(client, "jira_get_issue", OBJECT_MAPPER.createObjectNode());

            assertToolError(response);
        
        });
    }

    @Test
    @Feature("Tool Validation")
    @Story("MCP-034 — Валидация пустых и null значений аргументов")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-034: Валидация пустых и null значений аргументов")
    @Description("Предусловия: Classic lifecycle завершён; доступен jira_get_issue.\nШаги: по отдельности передать null, пустую строку и строку из пробельных символов в обязательный аргумент issue_key; после негативных вызовов выполнить валидный вызов.\nОжидаемый результат: каждый некорректный вызов возвращает MCP tool error (result.isError=true); следующий валидный вызов обрабатывается штатно.")
    @Tag("negative")
    @Tag("case-MCP-034")
    @Tag("jira")
    void shouldRejectNullEmptyAndBlankRequiredArguments(McpClient client) {
        Allure.step("Проверка null, пустого и пробельного значения обязательного аргумента", () -> {
            ObjectNode nullArgument = OBJECT_MAPPER.createObjectNode();
            nullArgument.putNull("issue_key");
            assertToolError(callTool(client, "jira_get_issue", nullArgument));

            ObjectNode emptyArgument = OBJECT_MAPPER.createObjectNode();
            emptyArgument.put("issue_key", "");
            assertToolError(callTool(client, "jira_get_issue", emptyArgument));

            ObjectNode blankArgument = OBJECT_MAPPER.createObjectNode();
            blankArgument.put("issue_key", "   ");
            assertToolError(callTool(client, "jira_get_issue", blankArgument));

            ObjectNode validArguments = OBJECT_MAPPER.createObjectNode();
            validArguments.put("issue_key", "AQA-1");
            assertSuccessfulJsonRpc(callTool(client, "jira_get_issue", validArguments));
        });
    }

    @Test
    @Feature("Tool Validation")
    @Story("MCP-012 — Вызов инструмента с неверным типом аргумента")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-012: Вызов инструмента с неверным типом аргумента")
    @Description("Предусловия: Инициализация завершена\nШаги: 1. Передать аргумент с типом, не соответствующим JSON Schema.\nОжидаемый результат: Запрос отклоняется; сервер возвращает контролируемую ошибку, не завершая процесс.")
    @Tag("negative")
    @Tag("case-MCP-012")
    @Tag("jira")
    void shouldRejectWrongToolArgumentType(McpClient client) {
        Allure.step("Вызов инструмента с неверным типом аргумента", () -> {
            ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
            arguments.put("issue_key", 42);
            assertToolError(callTool(client, "jira_get_issue", arguments));

            ObjectNode validArguments = OBJECT_MAPPER.createObjectNode();
            validArguments.put("issue_key", "AQA-1");
            assertSuccessfulJsonRpc(callTool(client, "jira_get_issue", validArguments));
        });
    }

    @Test
    @Feature("Tool Validation")
    @Story("MCP-013 — Вызов инструмента с неизвестным аргументом")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("MCP-013: Вызов инструмента с неизвестным аргументом")
    @Description("Предусловия: Инициализация завершена\nШаги: 1. Передать дополнительный аргумент, не объявленный схемой.\nОжидаемый результат: Сервер отклоняет вызов с `result.isError=true`, поскольку для всех 13 инструментов `additionalProperties=false`; обработка следующего корректного запроса продолжается.")
    @Tag("negative")
    @Tag("case-MCP-013")
    @Tag("jira")
    void shouldRejectUnknownToolArgument(McpClient client) {
        Allure.step("Вызов инструмента с неизвестным аргументом", () -> {
            ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
            arguments.put("issue_key", "AQA-1");
            arguments.put("unexpected", "value");
            assertToolError(callTool(client, "jira_get_issue", arguments));

            ObjectNode validArguments = OBJECT_MAPPER.createObjectNode();
            validArguments.put("issue_key", "AQA-1");
            assertSuccessfulJsonRpc(callTool(client, "jira_get_issue", validArguments));
        });
    }

    @Test
    @Feature("Tool Validation")
    @Story("MCP-034 — Отклонение строки из пробелов в обязательном аргументе")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-034: Отклонение строки из пробелов в обязательном аргументе")
    @Description("Предусловия: Classic lifecycle завершён; доступен jira_get_issue.\nШаги: вызвать jira_get_issue с issue_key, содержащим только пробельные символы.\nОжидаемый результат: сервер возвращает MCP tool error (result.isError=true); следующий валидный запрос может быть обработан.")
    @Tag("negative")
    @Tag("case-MCP-034")
    @Tag("jira")
    void shouldRejectBlankStringToolArgument(McpClient client) {
        Allure.step("Отклонение строки из пробелов в обязательном аргументе", () -> {
            ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
            arguments.put("issue_key", "   ");

            assertToolError(callTool(client, "jira_get_issue", arguments));
            ObjectNode validArguments = OBJECT_MAPPER.createObjectNode();
            validArguments.put("issue_key", "AQA-1");
            assertSuccessfulJsonRpc(callTool(client, "jira_get_issue", validArguments));
        });
    }

    @Test
    @Feature("Tool Validation")
    @Story("MCP-028 — Проверка границ параметра limit")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-028: Проверка границ параметра limit")
    @Description("Предусловия: MCP lifecycle завершён; доступны `jira_search`, `confluence_search`, `confluence_list_spaces`\nШаги: 1. Для каждого инструмента передать `limit=0`. 2. Повторить с `limit=101`. 3. Выполнить вызов с допустимым `limit=1`.\nОжидаемый результат: Значения вне диапазона 1–100 отклоняются как MCP tool error (`result.isError=true`); допустимое значение обрабатывается штатно.")
    @Tag("negative")
    @Tag("case-MCP-028")
    @Tag("jira")
    @Tag("confluence")
    void shouldRejectLimitOutsidePublishedBounds(McpClient client) {
        Allure.step("Проверка границ параметра limit", () -> {
            ObjectNode zeroLimit = OBJECT_MAPPER.createObjectNode();
            zeroLimit.put("jql", "project = AQA");
            zeroLimit.put("limit", 0);
            assertToolError(callTool(client, "jira_search", zeroLimit));

            ObjectNode excessiveLimit = OBJECT_MAPPER.createObjectNode();
            excessiveLimit.put("jql", "project = AQA");
            excessiveLimit.put("limit", 101);
            assertToolError(callTool(client, "jira_search", excessiveLimit));

            for (String toolName : Set.of("confluence_search", "confluence_list_spaces")) {
                String queryField = "confluence_search".equals(toolName) ? "cql" : null;
                for (int limit : new int[]{0, 101}) {
                    ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
                    if (queryField != null) {
                        arguments.put(queryField, "type = page");
                    }
                    arguments.put("limit", limit);
                    assertToolError(callTool(client, toolName, arguments));
                }
            }

            ObjectNode validJiraLimit = OBJECT_MAPPER.createObjectNode();
            validJiraLimit.put("jql", "project = AQA");
            validJiraLimit.put("limit", 1);
            JsonNode jiraResponse = callTool(client, "jira_search", validJiraLimit);
            assertSuccessfulJsonRpc(jiraResponse);
            assertThat(jiraResponse.path("result").path("isError").asBoolean()).isFalse();

            for (String toolName : Set.of("confluence_search", "confluence_list_spaces")) {
                ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
                if ("confluence_search".equals(toolName)) {
                    arguments.put("cql", "type = page");
                }
                arguments.put("limit", 1);
                JsonNode response = callTool(client, toolName, arguments);
                assertSuccessfulJsonRpc(response);
                assertThat(response.path("result").path("isError").asBoolean()).isFalse();
            }
        
        });
    }

    @Test
    @Feature("Confluence Page Updates")
    @Story("CONF-019 — Обновление страницы без полей для изменения")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("CONF-019: Обновление страницы без полей для изменения")
    @Description("Предусловия: Есть тестовая страница; известен её ID.\nШаги: вызвать confluence_update_page, передав только page_id.\nОжидаемый результат: сервер возвращает MCP tool error с сообщением о необходимости передать title или content; страница не изменяется.")
    @Tag("negative")
    @Tag("case-CONF-019")
    @Tag("confluence")
    void shouldRequireTitleOrContentForConfluenceUpdate(McpClient client) {
        Allure.step("Обновление страницы без полей для изменения", () -> {
            ObjectNode arguments = OBJECT_MAPPER.createObjectNode();
            arguments.put("page_id", "1");

            assertToolError(callTool(client, "confluence_update_page", arguments));
        
        });
    }

    private static JsonNode callTool(McpClient client, String name, ObjectNode arguments) {
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments);
        return client.requestStateless(
                "tools/call",
                params,
                STATELESS_PROTOCOL_VERSION,
                OBJECT_MAPPER.createObjectNode(),
                null,
                REQUEST_TIMEOUT
        );
    }

    private static JsonNode getStatelessTools(McpClient client) {
        JsonNode response = client.listToolsStateless(
                STATELESS_PROTOCOL_VERSION,
                REQUEST_TIMEOUT
        );
        assertSuccessfulJsonRpc(response);
        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        return tools;
    }

    private static void assertSuccessfulJsonRpc(JsonNode response) {
        assertThat(response.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.has("id")).isTrue();
        assertThat(response.has("error")).isFalse();
        assertThat(response.path("result").isObject()).isTrue();
    }

    private static void assertToolError(JsonNode response) {
        assertSuccessfulJsonRpc(response);
        JsonNode result = response.path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").isArray()).isTrue();
        assertThat(result.path("content").size()).isGreaterThan(0);
        assertThat(result.path("content").get(0).path("type").asText()).isEqualTo("text");
        assertThat(result.path("content").get(0).path("text").asText())
                .startsWith("Ошибка: ");
        assertThat(result.has("error")).isFalse();
    }

    private static void assertAnnotations(JsonNode annotations, ToolContract expected) {
        assertThat(annotations.path("readOnlyHint").isBoolean()).isTrue();
        assertThat(annotations.path("destructiveHint").isBoolean()).isTrue();
        assertThat(annotations.path("idempotentHint").isBoolean()).isTrue();
        assertThat(annotations.path("openWorldHint").isBoolean()).isTrue();
        assertThat(annotations.path("readOnlyHint").asBoolean())
                .isEqualTo(expected.readOnly());
        assertThat(annotations.path("destructiveHint").asBoolean())
                .isEqualTo(expected.destructive());
        assertThat(annotations.path("idempotentHint").asBoolean())
                .isEqualTo(expected.idempotent());
        assertThat(annotations.path("openWorldHint").asBoolean()).isTrue();
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

    private record ToolContract(Map<String, String> propertyTypes,
                                Set<String> required,
                                boolean readOnly,
                                boolean destructive,
                                boolean idempotent) {
    }
}
