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

import ru.solomentsev.aqa.testdata.McpClientTestData;
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
        return client.request(method, params, McpTestData.getRequestTimeout());
    }

    public static JsonNode sendRequest(McpClient client, String method,
                                       ObjectNode params, Duration timeout) {
        return client.request(method, params, timeout);
    }

    public static void sendNotification(McpClient client, String method, ObjectNode params) {
        client.notification(method, params);
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
        client.sendRawLine(line);
    }

    public static JsonNode awaitRawResponse(McpClient client, Duration timeout) {
        return client.awaitRawResponse(timeout);
    }

    public static JsonNode discoverStateless(McpClient client, String version, Duration timeout) {
        return Allure.step("Выполнить server/discover: protocolVersion=" + version,
                () -> client.discoverStateless(version, timeout));
    }

    public static JsonNode listToolsStateless(McpClient client) {
        return listToolsStateless(
                client,
                McpTestData.getStatelessProtocolVersion(),
                McpTestData.getRequestTimeout()
        );
    }

    public static JsonNode listToolsStateless(McpClient client, String version, Duration timeout) {
        return Allure.step("Выполнить stateless tools/list: protocolVersion=" + version,
                () -> client.listToolsStateless(version, timeout));
    }

    /**
     * Отвечает на один запрос в изолированном fake STDIO endpoint.
     * Исключение серверного потока сохраняется для явной проверки в тесте.
     *
     * @param serverStdin входной поток fake-сервера
     * @param serverStdout выходной поток fake-сервера
     * @param requestReceived ссылка для передачи полученного JSON-RPC request
     * @param serverFailure ссылка для передачи исключения серверного потока
     */
    public static void respondToSingleRequest(PipedInputStream serverStdin,
                                              PipedOutputStream serverStdout,
                                              AtomicReference<JsonNode> requestReceived,
                                              AtomicReference<Throwable> serverFailure) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(serverStdin, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(serverStdout, StandardCharsets.UTF_8))) {
            JsonNode request = OBJECT_MAPPER.readTree(reader.readLine());
            requestReceived.set(request);
            ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("jsonrpc", McpTestData.getJsonRpcVersion());
            response.set("id", request.get("id"));
            response.putObject("result").put("alive", McpClientTestData.isAliveValue());
            writer.write(OBJECT_MAPPER.writeValueAsString(response));
            writer.newLine();
            writer.flush();
        } catch (Exception exception) {
            serverFailure.set(exception);
        }
    }
}
