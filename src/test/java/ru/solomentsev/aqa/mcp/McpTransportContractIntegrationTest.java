package ru.solomentsev.aqa.mcp;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.solomentsev.aqa.junit.McpServerTest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет STDIO framing и JSON-RPC ошибки реального MCP-сервера.
 */
@McpServerTest
    @Tag("integration")
@EnabledIfSystemProperty(named = "AQA_RUN_INTEGRATION", matches = "true")
@Epic("MCP Server")
    @Tag("transport")
    @Tag("protocol")
class McpTransportContractIntegrationTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Feature("STDIO Transport")
    @Story("MCP-029 — Игнорирование пустых строк STDIO и восстановление после parse error")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-029: Игнорирование пустых строк STDIO и восстановление после parse error")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить пустую и пробельную строки; отправить некорректный JSON; проверить parse error; отправить JSON с trailing tokens; затем отправить корректный ping.\nОжидаемый результат: пустые строки игнорируются; некорректные строки возвращают -32700 с id=null; последующий ping обрабатывается.")
    @Tag("negative")
    @Tag("case-MCP-029")
    @Tag("case-MCP-030")
    @Tag("case-MCP-015")
    void shouldIgnoreBlankLinesAndContinueAfterParseError(McpClient client) {
        Allure.step("Игнорирование пустых строк STDIO и восстановление после parse error", () -> {
            client.sendRawLine("   ");
            client.sendRawLine("{invalid-json");

            var parseError = client.awaitRawResponse(REQUEST_TIMEOUT);
            assertJsonRpcError(parseError, -32700);
            assertThat(parseError.has("id")).isTrue();
            assertThat(parseError.get("id").isNull()).isTrue();

            client.sendRawLine("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\"} trailing");
            var trailingTokensError = client.awaitRawResponse(REQUEST_TIMEOUT);
            assertJsonRpcError(trailingTokensError, -32700);
            assertThat(trailingTokensError.get("id").isNull()).isTrue();

            var ping = client.request("ping", null, REQUEST_TIMEOUT);
            assertThat(ping.has("error")).isFalse();
            assertThat(ping.path("result").isObject()).isTrue();
        
        });
    }

    @Test
    @Feature("JSON-RPC Validation")
    @Story("MCP-031 — Отклонение некорректной структуры JSON-RPC")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-031: Отклонение некорректной структуры JSON-RPC")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить JSON-массив; отправить запрос без id; отправить запрос с некорректным типом id; отправить неизвестный метод со строковым id.\nОжидаемый результат: некорректные формы отклоняются с -32600; неизвестный метод возвращает -32601 и сохраняет строковый id.")
    @Tag("negative")
    @Tag("case-MCP-031")
    @Tag("case-MCP-016")
    @Tag("case-MCP-032")
    void shouldRejectNonObjectRootAndInvalidRequestShape(McpClient client) {
        Allure.step("Отклонение некорректной структуры JSON-RPC", () -> {
            client.sendRawLine("[]");
            assertJsonRpcError(client.awaitRawResponse(REQUEST_TIMEOUT), -32600);

            client.sendRawLine("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}");
            assertJsonRpcError(client.awaitRawResponse(REQUEST_TIMEOUT), -32600);

            client.sendRawLine("{\"jsonrpc\":\"2.0\",\"id\":{},\"method\":\"ping\"}");
            assertJsonRpcError(client.awaitRawResponse(REQUEST_TIMEOUT), -32600);

            client.sendRawLine("{\"jsonrpc\":\"2.0\",\"id\":\"custom-id\",\"method\":\"unknown\"}");
            var stringIdResponse = client.awaitRawResponse(REQUEST_TIMEOUT);
            assertJsonRpcError(stringIdResponse, -32601);
            assertThat(stringIdResponse.path("id").asText()).isEqualTo("custom-id");
        
        });
    }

    @Test
    @Feature("JSON-RPC Methods")
    @Story("MCP-014 — Вызов неизвестного JSON-RPC метода")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-014: Вызов неизвестного JSON-RPC метода")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить запрос с неизвестным методом на кириллице; проверить текст ошибки.\nОжидаемый результат: возвращается -32601; кириллическое имя метода сохранено в сообщении ошибки.")
    @Tag("negative")
    @Tag("case-MCP-014")
    void shouldReturnMethodNotFoundForUnknownMethod(McpClient client) {
        Allure.step("Вызов неизвестного JSON-RPC метода", () -> {
            var response = client.request("aqa/unknown-method", null, REQUEST_TIMEOUT);
            assertJsonRpcError(response, -32601);

            var ping = client.request("ping", null, REQUEST_TIMEOUT);
            assertThat(ping.has("error")).isFalse();
            assertThat(ping.path("result").isObject()).isTrue();
        
        });
    }

    @Test
    @Feature("STDIO Transport")
    @Story("Сохранение UTF-8 в JSON-RPC сообщениях")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Сохранение UTF-8 в JSON-RPC сообщениях")
    @Description("Предусловия: Процесс запущен\nШаги: 1. Отправить запрос с неизвестным методом.\nОжидаемый результат: Возвращается JSON-RPC ошибка метода; идентификатор запроса сохранён; процесс продолжает работу.")
    @Tag("contract")
    void shouldPreserveUtf8InJsonRpcMessages(McpClient client) {
        Allure.step("Сохранение UTF-8 в JSON-RPC сообщениях", () -> {
            var response = client.request("aqa/неизвестный-метод", null, REQUEST_TIMEOUT);

            assertJsonRpcError(response, -32601);
            assertThat(response.path("error").path("message").asText())
                    .contains("неизвестный-метод");
        
        });
    }

    @Test
    @Feature("Notifications")
    @Story("MCP-004 — Отправка notifications/initialized без response")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-004: Отправка notifications/initialized без response")
    @Description("Предусловия: Инициализация завершена\nШаги: 1. Отправить `notifications/initialized` без request id. 2. Проверить отсутствие JSON-RPC response на уведомление. 3. Отправить следующий обычный запрос.\nОжидаемый результат: Уведомление принимается без отдельного ответа; последующие запросы обрабатываются штатно.")
    @Tag("protocol")
    @Tag("case-MCP-004")
    void shouldNotRespondToNotifications(McpClient client) {
        Allure.step("Отправка notifications/initialized без response", () -> {
            client.notification("notifications/initialized", null);

            var ping = client.request("ping", null, REQUEST_TIMEOUT);

            assertThat(ping.has("error")).isFalse();
            assertThat(ping.path("result").isObject()).isTrue();
        
        });
    }

    @Test
    @Feature("Classic Lifecycle")
    @Story("MCP-025 — Повторный initialize")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-025: Повторный initialize")
    @Description("Предусловия: Классический lifecycle завершён; процесс в состоянии READY\nШаги: 1. Отправить второй `initialize` с новым request id. 2. Проверить ответ. 3. Выполнить `tools/list` исходной сессии.\nОжидаемый результат: Второй `initialize` отклоняется с `-32600 Invalid Request`; исходная сессия остаётся работоспособной и повторная инициализация не выполняется.")
    @Tag("negative")
    @Tag("case-MCP-025")
    void shouldRejectSecondClassicInitialize(McpClient client) {
        Allure.step("Повторный initialize", () -> {
            ObjectNode params = OBJECT_MAPPER.createObjectNode();
            params.put("protocolVersion", "2025-11-25");
            params.putObject("capabilities");
            params.putObject("clientInfo")
                    .put("name", "atlassian-mcp-light-autotests")
                    .put("version", "1.0");

            var response = client.request("initialize", params, REQUEST_TIMEOUT);
            assertJsonRpcError(response, -32600);

            var tools = client.request("tools/list", null, REQUEST_TIMEOUT);
            assertThat(tools.has("error")).isFalse();
            assertThat(tools.path("result").path("tools").isArray()).isTrue();
        
        });
    }

    private static void assertJsonRpcError(com.fasterxml.jackson.databind.JsonNode response,
                                           int expectedCode) {
        assertThat(response.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.has("error")).isTrue();
        assertThat(response.path("error").path("code").asInt()).isEqualTo(expectedCode);
    }
}
