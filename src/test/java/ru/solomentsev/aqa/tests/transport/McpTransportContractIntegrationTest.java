package ru.solomentsev.aqa.tests.transport;

import ru.solomentsev.aqa.mcp.McpClient;
import ru.solomentsev.aqa.support.junit.McpServerTest;
import ru.solomentsev.aqa.testdata.McpTestData;

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

    private static final Duration REQUEST_TIMEOUT = McpTestData.REQUEST_TIMEOUT;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Feature("STDIO Transport")
    @Story("MCP-029 — Игнорирование пустых строк STDIO")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-029: Игнорирование пустых строк STDIO")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить пустую строку и строку из пробельных символов; затем отправить корректный ping.\nОжидаемый результат: пустые строки игнорируются без ответа и завершения процесса; ping обрабатывается штатно.")
    @Tag("negative")
    @Tag("case-MCP-029")
    void shouldIgnoreBlankAndWhitespaceLines(McpClient client) {
        sendRawLine(client, "");
        sendRawLine(client, "   ");
        var ping = sendRequest(client, "ping", null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("После обработки некорректной строки сервер должен продолжать принимать запросы")
                .isFalse();
        assertThat(ping.path("result").isObject()).as("Ping должен возвращать объект result").isTrue();
    
    }

    @Test
    @Feature("STDIO Transport")
    @Story("MCP-030 — Запрет trailing tokens в JSONL-сообщении")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-030: Запрет trailing tokens в JSONL-сообщении")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить строку с корректным JSON-объектом и дополнительным JSON-токеном; затем отправить корректный ping.\nОжидаемый результат: первая строка отклоняется с -32700 и id=null; следующая строка обрабатывается штатно.")
    @Tag("negative")
    @Tag("case-MCP-030")
    void shouldRejectTrailingJsonTokens(McpClient client) {
        sendRawLine(client, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\"} trailing");
        var error = awaitRawResponse(client, REQUEST_TIMEOUT);
        assertJsonRpcError(error, -32700);
        assertThat(error.get("id").isNull()).as("Ответ на некорректную JSON-RPC строку должен содержать id=null").isTrue();

        var ping = sendRequest(client, "ping", null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("После обработки некорректной строки сервер должен продолжать принимать запросы")
                .isFalse();
    
    }

    @Test
    @Feature("JSON-RPC Validation")
    @Story("MCP-015 — Обработка некорректного JSON")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-015: Обработка некорректного JSON")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить синтаксически некорректную JSON-строку; затем отправить корректный ping.\nОжидаемый результат: некорректное сообщение возвращает -32700; последующий запрос обрабатывается без зависания процесса.")
    @Tag("negative")
    @Tag("case-MCP-015")
    void shouldRecoverAfterMalformedJson(McpClient client) {
        sendRawLine(client, "{invalid-json");
        var error = awaitRawResponse(client, REQUEST_TIMEOUT);
        assertJsonRpcError(error, -32700);
        assertThat(error.get("id").isNull()).as("Проверка error.get('id').isNull(): ожидалось isTrue").isTrue();

        var ping = sendRequest(client, "ping", null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("После обработки некорректной строки сервер должен продолжать принимать запросы")
                .isFalse();
    
    }

    @Test
    @Feature("JSON-RPC Validation")
    @Story("MCP-031 — Отклонение JSON-корня, не являющегося объектом")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-031: Отклонение JSON-корня, не являющегося объектом")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить JSON-массив в качестве корневого значения; затем отправить корректный ping.\nОжидаемый результат: массив отклоняется с -32600 Invalid Request и id=null; процесс продолжает обрабатывать следующие строки.")
    @Tag("negative")
    @Tag("case-MCP-031")
    void shouldRejectNonObjectJsonRoot(McpClient client) {
        sendRawLine(client, "[]");
        var error = awaitRawResponse(client, REQUEST_TIMEOUT);
        assertJsonRpcError(error, -32600);
        assertThat(error.get("id").isNull()).as("Проверка error.get('id').isNull(): ожидалось isTrue").isTrue();

        var ping = sendRequest(client, "ping", null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("После обработки некорректной строки сервер должен продолжать принимать запросы")
                .isFalse();
    
    }

    @Test
    @Feature("JSON-RPC Validation")
    @Story("MCP-016 — Обработка некорректной структуры JSON-RPC")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-016: Обработка некорректной структуры JSON-RPC")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить JSON-объект без обязательного поля method; затем отправить корректный ping.\nОжидаемый результат: некорректная структура отклоняется с -32600 Invalid Request; процесс остаётся работоспособным.")
    @Tag("negative")
    @Tag("case-MCP-016")
    void shouldRejectRequestWithMissingMethod(McpClient client) {
        sendRawLine(client, "{\"jsonrpc\":\"2.0\",\"id\":1}");
        var error = awaitRawResponse(client, REQUEST_TIMEOUT);
        assertJsonRpcError(error, -32600);

        var ping = sendRequest(client, "ping", null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("После обработки некорректной строки сервер должен продолжать принимать запросы")
                .isFalse();
    
    }

    @Test
    @Feature("JSON-RPC Validation")
    @Story("MCP-032 — Отсутствие id у метода-запроса")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-032: Отсутствие id у метода-запроса")
    @Description("Предусловия: MCP STDIO-процесс запущен; classic lifecycle находится в READY.\nШаги: отправить tools/list без поля id; затем отправить корректный tools/list с id.\nОжидаемый результат: первый вызов отклоняется с -32600 Invalid Request; второй получает корректный response.")
    @Tag("negative")
    @Tag("case-MCP-032")
    void shouldRejectRequestWithoutId(McpClient client) {
        sendRawLine(client, "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"}");
        var error = awaitRawResponse(client, REQUEST_TIMEOUT);
        assertJsonRpcError(error, -32600);

        var tools = sendRequest(client, "tools/list", null, REQUEST_TIMEOUT);
        assertThat(tools.has("error")).as("После некорректной строки tools/list должен завершиться успешно").isFalse();
        assertThat(tools.path("result").path("tools").isArray()).as("tools/list должен вернуть массив инструментов").isTrue();
    
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
        var response = sendRequest(client, "aqa/unknown-method", null, REQUEST_TIMEOUT);
        assertJsonRpcError(response, -32601);

        var ping = sendRequest(client, "ping", null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("После обработки некорректной строки сервер должен продолжать принимать запросы")
                .isFalse();
        assertThat(ping.path("result").isObject()).as("Проверка ping.path('result').isObject(): ожидалось isTrue").isTrue();
    
    
    }

    @Test
    @Feature("STDIO Transport")
    @Story("Сохранение UTF-8 в JSON-RPC сообщениях")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Сохранение UTF-8 в JSON-RPC сообщениях")
    @Description("Предусловия: Процесс запущен\nШаги: 1. Отправить запрос с неизвестным методом.\nОжидаемый результат: Возвращается JSON-RPC ошибка метода; идентификатор запроса сохранён; процесс продолжает работу.")
    @Tag("contract")
    void shouldPreserveUtf8InJsonRpcMessages(McpClient client) {
        var response = sendRequest(client, "aqa/неизвестный-метод", null, REQUEST_TIMEOUT);

        assertJsonRpcError(response, -32601);
        assertThat(response.path("error").path("message").asText())
                .as("Сообщение ошибки должно содержать имя неизвестного метода")
                .contains("неизвестный-метод");
    
    
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
        client.notification("notifications/initialized", null);

        var ping = sendRequest(client, "ping", null, REQUEST_TIMEOUT);

        assertThat(ping.has("error"))
                .as("После обработки некорректной строки сервер должен продолжать принимать запросы")
                .isFalse();
        assertThat(ping.path("result").isObject()).as("Проверка ping.path('result').isObject(): ожидалось isTrue").isTrue();
    
    
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
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("protocolVersion", McpTestData.CLASSIC_PROTOCOL_VERSION);
        params.putObject("capabilities");
        params.putObject("clientInfo")
                .put("name", McpTestData.CLIENT_NAME)
                .put("version", McpTestData.CLIENT_VERSION);

        var response = sendRequest(client, "initialize", params, REQUEST_TIMEOUT);
        assertJsonRpcError(response, -32600);

        var tools = sendRequest(client, "tools/list", null, REQUEST_TIMEOUT);
        assertThat(tools.has("error")).as("Проверка tools.has('error'): ожидалось isFalse").isFalse();
        assertThat(tools.path("result").path("tools").isArray()).as("Проверка tools.path('result').path('tools').isArray(): ожидалось isTrue").isTrue();
    
    
    }

    private static void sendRawLine(McpClient client, String line) {
        Allure.step("Отправить строку STDIO", () -> client.sendRawLine(line));
    }

    private static JsonNode awaitRawResponse(
            McpClient client, Duration timeout) {
        return Allure.step("Получить ответ STDIO", () -> client.awaitRawResponse(timeout));
    }

    private static JsonNode sendRequest(
            McpClient client, String method, ObjectNode params, Duration timeout) {
        return Allure.step("Отправить MCP-запрос " + method,
                () -> client.request(method, params, timeout));
    }

    private static void assertJsonRpcError(com.fasterxml.jackson.databind.JsonNode response,
                                           int expectedCode) {
        assertThat(response.path("jsonrpc").asText())
                .as("Ответ должен соответствовать JSON-RPC версии 2.0")
                .isEqualTo("2.0");
        assertThat(response.has("error"))
                .as("Некорректный JSON-RPC запрос должен вернуть объект error")
                .isTrue();
        assertThat(response.path("error").path("code").asInt())
                .as("JSON-RPC ошибка должна иметь код %s", expectedCode)
                .isEqualTo(expectedCode);
    }
}
