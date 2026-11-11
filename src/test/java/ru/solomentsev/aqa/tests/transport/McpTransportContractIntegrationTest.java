package ru.solomentsev.aqa.mcp;

import ru.solomentsev.aqa.support.junit.McpServerTest;
import ru.solomentsev.aqa.testdata.McpTestData;

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

import java.time.Duration;

import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThat;

/**
 * Проверяет STDIO framing и JSON-RPC ошибки реального MCP-сервера.
 */
@McpServerTest
@Tag("integration")
@EnabledIfSystemProperty(named = "AQA_RUN_INTEGRATION", matches = "true")
@Epic("MCP Server")
@Tag("transport")
@Tag("protocol")
@Tag("regression")
class McpTransportContractIntegrationTest {

    private static final Duration REQUEST_TIMEOUT = McpTestData.getRequestTimeout();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Feature("STDIO Transport")
    @Story("MCP-029 — Пустые строки не мешают обработке следующего STDIO-запроса")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-029: после пустой строки и строки из пробелов сервер успешно обрабатывает ping")
    @Description("Предусловия: MCP STDIO-процесс запущен и готов принимать JSONL-сообщения.\nШаги: записать в STDIN пустую строку, затем строку из трёх пробелов; после них отправить JSON-RPC request method=ping.\nОжидаемый результат: сервер продолжает обрабатывать STDIN и возвращает на контрольный ping успешный response с объектом result. Этот тест подтверждает, что пустые строки не блокируют последующий запрос; отсутствие отдельного ответа на каждую пустую строку напрямую не проверяется.")
    @Tag("negative")
    @Tag("case-MCP-029")
    void shouldProcessPingAfterBlankAndWhitespaceLines(McpClient client) {
        Allure.parameter("Пустая входная строка", "\"\"");
        Allure.parameter("Строка из пробелов", "\"   \"");
        Allure.parameter("Контрольный запрос", McpTestData.getPingMethod());
        sendRawLine(client, "");
        sendRawLine(client, "   ");
        var ping = sendRequest(client, McpTestData.getPingMethod(), null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("Контрольный ping после предыдущего некорректного сообщения должен завершиться без error, фактически: %s", ping.path("error"))
                .isFalse();
        assertThat(ping.path("result").isObject()).as("Ping должен возвращать объект result").isTrue();
    
    }

    @Test
    @Feature("STDIO Transport")
    @Story("MCP-030 — Запрет trailing tokens в JSONL-сообщении")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-030: STDIO отклоняет JSON-объект с trailing token [id=5, error=-32700], затем обрабатывает ping")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить одну JSONL-строку, содержащую JSON-RPC ping с id=5 и лишний текстовый токен trailing; прочитать ошибку; затем отправить корректный ping.\nОжидаемый результат: некорректная строка возвращает JSON-RPC parse error=-32700 с id=null; следующий ping возвращает успешный result, подтверждая, что обработка STDIN продолжается.")
    @Tag("negative")
    @Tag("case-MCP-030")
    void shouldRejectTrailingJsonTokens(McpClient client) {
        Allure.parameter("Некорректная STDIO-строка", "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\"} trailing");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getJsonRpcParseError());
        sendRawLine(client, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\"} trailing");
        var error = awaitRawResponse(client, REQUEST_TIMEOUT);
        assertJsonRpcError(error, McpTestData.getJsonRpcParseError());
        assertThat(error.get("id").isNull()).as("Ответ на некорректную JSON-RPC строку должен содержать id=null").isTrue();

        var ping = sendRequest(client, McpTestData.getPingMethod(), null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("Контрольный ping после предыдущего запроса должен завершиться без JSON-RPC error")
                .isFalse();
        assertThat(ping.path("result").isObject())
                .as("Контрольный ping после предыдущего запроса должен вернуть объект result")
                .isTrue();
    
    }

    @Test
    @Feature("JSON-RPC Validation")
    @Story("MCP-015 — Обработка некорректного JSON")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-015: STDIO отклоняет синтаксически некорректный JSON {invalid-json [error=-32700], затем обрабатывает ping")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить строку {invalid-json; прочитать JSON-RPC ошибку; затем отправить method=ping.\nОжидаемый результат: первая строка возвращает parse error=-32700 и id=null; второй запрос получает успешный JSON-RPC response с объектом result.")
    @Tag("negative")
    @Tag("case-MCP-015")
    void shouldRecoverAfterMalformedJson(McpClient client) {
        Allure.parameter("Некорректная JSON-строка", "{invalid-json");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getJsonRpcParseError());
        sendRawLine(client, "{invalid-json");
        var error = awaitRawResponse(client, REQUEST_TIMEOUT);
        assertJsonRpcError(error, McpTestData.getJsonRpcParseError());
        assertThat(error.get("id").isNull()).as("Ошибка парсинга должна содержать id=null, поскольку запрос не удалось разобрать").isTrue();

        var ping = sendRequest(client, McpTestData.getPingMethod(), null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("Контрольный ping после некорректного JSON-RPC сообщения должен завершиться без error")
                .isFalse();
        assertThat(ping.path("result").isObject())
                .as("Контрольный ping после некорректного JSON-RPC сообщения должен вернуть объект result")
                .isTrue();
    
    }

    @Test
    @Feature("JSON-RPC Validation")
    @Story("MCP-031 — Отклонение JSON-корня, не являющегося объектом")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-031: STDIO отклоняет корневой JSON-массив [] [error=-32600], затем обрабатывает ping")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить JSON-значение [] вместо JSON-RPC объекта; прочитать ошибку; затем отправить method=ping.\nОжидаемый результат: массив отклоняется с JSON-RPC error=-32600 и id=null; последующий ping получает успешный response с объектом result.")
    @Tag("negative")
    @Tag("case-MCP-031")
    void shouldRejectNonObjectJsonRoot(McpClient client) {
        Allure.parameter("JSON root", "[] (массив вместо объекта)");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getJsonRpcInvalidRequest());
        sendRawLine(client, "[]");
        var error = awaitRawResponse(client, REQUEST_TIMEOUT);
        assertJsonRpcError(error, McpTestData.getJsonRpcInvalidRequest());
        assertThat(error.get("id").isNull()).as("Ошибка парсинга должна содержать id=null, поскольку запрос не удалось разобрать").isTrue();

        var ping = sendRequest(client, McpTestData.getPingMethod(), null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("Контрольный ping после некорректного JSON-RPC сообщения должен завершиться без error")
                .isFalse();
        assertThat(ping.path("result").isObject())
                .as("Контрольный ping после некорректного JSON-RPC сообщения должен вернуть объект result")
                .isTrue();
    
    }

    @Test
    @Feature("JSON-RPC Validation")
    @Story("MCP-016 — Обработка некорректной структуры JSON-RPC")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-016: STDIO отклоняет JSON-RPC request без method [id=1, error=-32600], затем обрабатывает ping")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить объект {jsonrpc=2.0, id=1} без обязательного method; прочитать ошибку; затем отправить method=ping.\nОжидаемый результат: запрос без method отклоняется с JSON-RPC error=-32600; следующий ping получает успешный response с объектом result.")
    @Tag("negative")
    @Tag("case-MCP-016")
    void shouldRejectRequestWithMissingMethod(McpClient client) {
        Allure.parameter("JSON-RPC request", "{\"jsonrpc\":\"2.0\",\"id\":1}");
        Allure.parameter("Отсутствующее поле", "method");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getJsonRpcInvalidRequest());
        sendRawLine(client, "{\"jsonrpc\":\"2.0\",\"id\":1}");
        var error = awaitRawResponse(client, REQUEST_TIMEOUT);
        assertJsonRpcError(error, McpTestData.getJsonRpcInvalidRequest());

        var ping = sendRequest(client, McpTestData.getPingMethod(), null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("Контрольный ping после некорректного JSON-RPC сообщения должен завершиться без error")
                .isFalse();
        assertThat(ping.path("result").isObject())
                .as("Контрольный ping после некорректного JSON-RPC сообщения должен вернуть объект result")
                .isTrue();
    
    }

    @Test
    @Feature("JSON-RPC Validation")
    @Story("MCP-032 — Отсутствие id у метода-запроса")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-032: tools/list без request id отклоняется [error=-32600]; tools/list с id выполняется")
    @Description("Предусловия: MCP STDIO-процесс прошёл initialize и notifications/initialized и находится в READY.\nШаги: отправить JSON-RPC tools/list без поля id; прочитать ошибку; затем отправить tools/list с request id через MCP Client.\nОжидаемый результат: сообщение без id отклоняется с JSON-RPC error=-32600; корректный запрос возвращает успешный response, в котором result.tools является массивом.")
    @Tag("negative")
    @Tag("case-MCP-032")
    void shouldRejectRequestWithoutId(McpClient client) {
        Allure.parameter("JSON-RPC method", McpTestData.getToolsListMethod());
        Allure.parameter("Отсутствующее поле", "id");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getJsonRpcInvalidRequest());
        sendRawLine(client, "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"}");
        var error = awaitRawResponse(client, REQUEST_TIMEOUT);
        assertJsonRpcError(error, McpTestData.getJsonRpcInvalidRequest());

        var tools = sendRequest(client, McpTestData.getToolsListMethod(), null, REQUEST_TIMEOUT);
        assertThat(tools.has("error")).as("После некорректной строки tools/list должен завершиться успешно").isFalse();
        assertThat(tools.path("result").path("tools").isArray()).as("tools/list должен вернуть массив инструментов").isTrue();
    
    }

    @Test
    @Feature("JSON-RPC Methods")
    @Story("MCP-014 — Вызов неизвестного JSON-RPC метода")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-014: Неизвестный JSON-RPC method=aqa/unknown-method возвращает error=-32601; следующий ping успешен")
    @Description("Предусловия: MCP STDIO-процесс запущен.\nШаги: отправить JSON-RPC request с method=aqa/unknown-method; проверить error.code; затем отправить ping.\nОжидаемый результат: неизвестный метод возвращает error=-32601; следующий ping возвращает успешный объект result, подтверждая сохранение работоспособности сервера.")
    @Tag("negative")
    @Tag("case-MCP-014")
    void shouldReturnMethodNotFoundForUnknownMethod(McpClient client) {
        Allure.parameter("JSON-RPC method", "aqa/unknown-method");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getJsonRpcMethodNotFound());
        var response = sendRequest(client, "aqa/unknown-method", null, REQUEST_TIMEOUT);
        assertJsonRpcError(response, McpTestData.getJsonRpcMethodNotFound());

        var ping = sendRequest(client, McpTestData.getPingMethod(), null, REQUEST_TIMEOUT);
        assertThat(ping.has("error"))
                .as("Контрольный ping после некорректного JSON-RPC сообщения должен завершиться без error")
                .isFalse();
        assertThat(ping.path("result").isObject())
                .as("Контрольный ping после некорректного JSON-RPC сообщения должен вернуть объект result")
                .isTrue();
    
    
    }

    @Test
    @Feature("STDIO Transport")
    @Story("Сохранение UTF-8 в JSON-RPC сообщениях")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Сохранение UTF-8 в имени неизвестного метода [method=aqa/неизвестный-метод, error=-32601]")
    @Description("Предусловия: MCP STDIO-процесс запущен; обмен выполняется в UTF-8.\nШаги: отправить JSON-RPC request с кириллическим method=aqa/неизвестный-метод; прочитать response.\nОжидаемый результат: сервер возвращает error=-32601; текст error.message содержит исходное кириллическое имя метода без потери или искажения символов.")
    @Tag("contract")
    void shouldPreserveUtf8InJsonRpcMessages(McpClient client) {
        Allure.parameter("JSON-RPC method", "aqa/неизвестный-метод");
        Allure.parameter("Кодировка", "UTF-8");
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getJsonRpcMethodNotFound());
        var response = sendRequest(client, "aqa/неизвестный-метод", null, REQUEST_TIMEOUT);

        assertJsonRpcError(response, McpTestData.getJsonRpcMethodNotFound());
        assertThat(response.path("error").path("message").asText())
                .as("Сообщение ошибки должно содержать имя неизвестного метода")
                .contains("неизвестный-метод");
    
    
    }

    @Test
    @Feature("Notifications")
    @Story("MCP-004 — Передача notifications/initialized без request id и сохранение работоспособности сессии")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-004: notifications/initialized без request id не нарушает обработку следующего ping")
    @Description("Предусловия: MCP STDIO-процесс завершил initialize и находится в READY.\nШаги: отправить notifications/initialized без request id; затем отправить ping с request id.\nОжидаемый результат: notification отправляется без поля id; следующий ping получает успешный JSON-RPC response с объектом result. Отдельное отсутствие ответа на notification этим тестом напрямую не проверяется.")
    @Tag("protocol")
    @Tag("case-MCP-004")
    @Tag("smoke")
    void shouldSendInitializedNotificationWithoutRequestIdAndKeepSessionReady(McpClient client) {
        Allure.parameter("Notification method", McpTestData.getInitializedNotificationMethod());
        Allure.parameter("Notification request id", "отсутствует");
        Allure.parameter("Контрольный запрос", McpTestData.getPingMethod());
        Allure.step("Отправить notification notifications/initialized без request id", () -> client.notification(McpTestData.getInitializedNotificationMethod(), null));

        var ping = sendRequest(client, McpTestData.getPingMethod(), null, REQUEST_TIMEOUT);

        assertThat(ping.has("error"))
                .as("Контрольный ping после некорректного JSON-RPC сообщения должен завершиться без error")
                .isFalse();
        assertThat(ping.path("result").isObject())
                .as("Контрольный ping после некорректного JSON-RPC сообщения должен вернуть объект result")
                .isTrue();
    
    
    }

    @Test
    @Feature("Classic Lifecycle")
    @Story("MCP-025 — Повторный initialize")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("MCP-025: Повторный initialize отклоняется с error=-32600; исходная сессия сохраняет tools/list")
    @Description("Предусловия: MCP STDIO-процесс завершил classic initialize и notifications/initialized; lifecycle находится в READY.\nШаги: повторно отправить initialize с новым request id; затем вызвать tools/list в исходной сессии.\nОжидаемый результат: повторный initialize отклоняется с JSON-RPC error=-32600; tools/list по-прежнему возвращает успешный response с массивом инструментов.")
    @Tag("negative")
    @Tag("case-MCP-025")
    void shouldRejectSecondClassicInitialize(McpClient client) {
        Allure.parameter("Повторный JSON-RPC method", McpTestData.getInitializeMethod());
        Allure.parameter("Ожидаемый JSON-RPC error code", McpTestData.getJsonRpcInvalidRequest());
        Allure.parameter("Проверка сохранения сессии", McpTestData.getToolsListMethod());
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("protocolVersion", McpTestData.getClassicProtocolVersion());
        params.putObject("capabilities");
        params.putObject("clientInfo")
                .put("name", McpTestData.getClientName())
                .put("version", McpTestData.getClientVersion());

        var response = sendRequest(client, McpTestData.getInitializeMethod(), params, REQUEST_TIMEOUT);
        assertJsonRpcError(response, McpTestData.getJsonRpcInvalidRequest());

        var tools = sendRequest(client, McpTestData.getToolsListMethod(), null, REQUEST_TIMEOUT);
        assertThat(tools.has("error")).as("После некорректного запроса tools/list должен завершиться без JSON-RPC error").isFalse();
        assertThat(tools.path("result").path("tools").isArray()).as("Успешный tools/list должен вернуть массив инструментов").isTrue();
    
    
    }

    @Step("Отправить необработанную STDIO-строку")
    private static void sendRawLine(McpClient client, String line) {
        client.sendRawLine(line);
    }

    @Step("Дождаться JSON-RPC response после отправки необработанной STDIO-строки")
    private static JsonNode awaitRawResponse(McpClient client, Duration timeout) {
        return client.awaitRawResponse(timeout);
    }

    @Step("Отправить JSON-RPC request: method={method}, timeout={timeout}")
    private static JsonNode sendRequest(
            McpClient client, String method, ObjectNode params, Duration timeout) {
        return client.request(method, params, timeout);
    }

    @Step("Проверить JSON-RPC error response: ожидаемый code={expectedCode}")
    private static void assertJsonRpcError(com.fasterxml.jackson.databind.JsonNode response,
                                           int expectedCode) {
        assertThat(response.path("jsonrpc").asText())
                .as("Ответ должен соответствовать JSON-RPC версии 2.0")
                .isEqualTo(McpTestData.getJsonRpcVersion());
        assertThat(response.has("error"))
                .as("JSON-RPC response с ожидаемой ошибкой должен содержать объект error")
                .isTrue();
        assertThat(response.path("error").path("code").asInt())
                .as("Для некорректного сообщения ожидается JSON-RPC error code=%s, фактически: %s", expectedCode, response.path("error").path("code"))
                .isEqualTo(expectedCode);
    }
}
