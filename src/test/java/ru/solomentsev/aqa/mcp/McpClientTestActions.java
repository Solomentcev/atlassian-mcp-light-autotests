package ru.solomentsev.aqa.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.qameta.allure.Step;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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

    @Step("Отправить JSON-RPC request: method={method}")
    public static JsonNode sendRequest(McpClient client, String method, ObjectNode params) {
        return client.request(method, params, McpTestData.getRequestTimeout());
    }

    @Step("Отправить JSON-RPC request: method={method}, timeout={timeout}")
    public static JsonNode sendRequest(McpClient client, String method,
                                       ObjectNode params, Duration timeout) {
        return client.request(method, params, timeout);
    }

    @Step("Отправить JSON-RPC notification: method={method}")
    public static void sendNotification(McpClient client, String method, ObjectNode params) {
        client.notification(method, params);
    }

    @Step("Выполнить classic initialize: protocolVersion={version}, client={clientName}/{clientVersion}")
    public static JsonNode initialize(McpClient client, String version, String clientName,
                                      String clientVersion, Duration timeout) {
        return client.initialize(version, clientName, clientVersion, timeout);
    }

    @Step("Отправить stateless MCP request: method={method}, protocolVersion={version}")
    public static JsonNode requestStateless(McpClient client, String method, ObjectNode params,
                                            String version, ObjectNode capabilities,
                                            ObjectNode clientInfo, Duration timeout) {
        return client.requestStateless(method, params, version, capabilities, clientInfo, timeout);
    }

    @Step("Отправить необработанную STDIO-строку")
    public static void sendRawLine(McpClient client, String line) {
        client.sendRawLine(line);
    }

    @Step("Дождаться JSON-RPC response после отправки необработанной STDIO-строки")
    public static JsonNode awaitRawResponse(McpClient client, Duration timeout) {
        return client.awaitRawResponse(timeout);
    }

    @Step("Выполнить server/discover: protocolVersion={version}, timeout={timeout}")
    public static JsonNode discoverStateless(McpClient client, String version, Duration timeout) {
        return client.discoverStateless(version, timeout);
    }

    @Step("Выполнить stateless tools/list: protocolVersion={version}, timeout={timeout}")
    public static JsonNode listToolsStateless(McpClient client, String version, Duration timeout) {
        return client.listToolsStateless(version, timeout);
    }

    /**
     * Отвечает на один запрос в изолированном fake STDIO endpoint.
     * Исключение в серверном потоке проявится как timeout/ошибка обмена в тесте клиента.
     */
    public static void respondToSingleRequest(PipedInputStream serverStdin,
                                              PipedOutputStream serverStdout) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(serverStdin, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(serverStdout, StandardCharsets.UTF_8))) {
            JsonNode request = OBJECT_MAPPER.readTree(reader.readLine());
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
