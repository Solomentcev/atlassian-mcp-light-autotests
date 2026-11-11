package ru.solomentsev.aqa.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.qameta.allure.Allure;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import ru.solomentsev.aqa.testdata.McpTestData;

/**
 * Повторно используемые действия MCP Client для тестовых сценариев.
 *
 * <p>Класс находится в пакете MCP, чтобы использовать package-private
 * raw STDIO операции клиента без расширения его публичного API.</p>
 */
public final class McpClientTestActions {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private McpClientTestActions() {
    }

    public static JsonNode sendRequest(McpClient client, String method, ObjectNode params) {
        return Allure.step("Отправить JSON-RPC request: method=" + method,
                () -> client.request(method, params, McpTestData.getRequestTimeout()));
    }

    public static JsonNode sendRequest(McpClient client, String method,
                                       ObjectNode params, Duration timeout) {
        return Allure.step("Отправить JSON-RPC request: method=" + method,
                () -> client.request(method, params, timeout));
    }

    public static void sendNotification(McpClient client, String method, ObjectNode params) {
        Allure.step("Отправить JSON-RPC notification: method=" + method,
                () -> client.notification(method, params));
    }

    public static JsonNode initialize(McpClient client, String version, String clientName,
                                      String clientVersion, Duration timeout) {
        return Allure.step("Выполнить classic initialize: protocolVersion=" + version,
                () -> client.initialize(version, clientName, clientVersion, timeout));
    }

    public static JsonNode requestStateless(McpClient client, String method, ObjectNode params,
                                            String version, ObjectNode capabilities,
                                            ObjectNode clientInfo, Duration timeout) {
        return Allure.step("Отправить stateless MCP request: method=" + method
                        + ", protocolVersion=" + version,
                () -> client.requestStateless(method, params, version, capabilities, clientInfo, timeout));
    }

    public static void sendRawLine(McpClient client, String line) {
        Allure.step("Отправить необработанную STDIO-строку", () -> client.sendRawLine(line));
    }

    public static JsonNode awaitRawResponse(McpClient client, Duration timeout) {
        return Allure.step("Дождаться JSON-RPC response после отправки необработанной STDIO-строки",
                () -> client.awaitRawResponse(timeout));
    }

    public static JsonNode discoverStateless(McpClient client, String version, Duration timeout) {
        return Allure.step("Выполнить server/discover: protocolVersion=" + version,
                () -> client.discoverStateless(version, timeout));
    }

    public static JsonNode listToolsStateless(McpClient client, String version, Duration timeout) {
        return Allure.step("Выполнить stateless tools/list: protocolVersion=" + version,
                () -> client.listToolsStateless(version, timeout));
    }

    /**
     * Отвечает на один запрос в изолированном fake STDIO endpoint.
     * Исключение в серверном потоке проявится как timeout/ошибка обмена в тесте клиента.
     */
    public static void respondToSingleRequest(PipedInputStream serverStdin,
                                              PipedOutputStream serverStdout,
                                              AtomicReference<JsonNode> requestReceived) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(serverStdin, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(serverStdout, StandardCharsets.UTF_8))) {
            JsonNode request = OBJECT_MAPPER.readTree(reader.readLine());
            requestReceived.set(request);
            ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("jsonrpc", McpTestData.getJsonRpcVersion());
            response.set("id", request.get("id"));
            response.putObject("result").put("alive", true);
            writer.write(OBJECT_MAPPER.writeValueAsString(response));
            writer.newLine();
            writer.flush();
        } catch (Exception ignored) {
            // Ошибка обмена будет зафиксирована проверкой на стороне MCP Client.
        }
    }
}
