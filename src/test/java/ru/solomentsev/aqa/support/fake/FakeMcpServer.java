package ru.solomentsev.aqa.support.fake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.solomentsev.aqa.testdata.McpTestData;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Минимальный дочерний MCP-процесс для проверки запуска и завершения сессии.
 */
public final class FakeMcpServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FakeMcpServer() {
    }

    public static void main(String[] args) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            JsonNode initializeRequest = OBJECT_MAPPER.readTree(reader.readLine());
            if (!McpTestData.INITIALIZE_METHOD.equals(initializeRequest.path("method").asText())) {
                System.exit(2);
            }

            var response = OBJECT_MAPPER.createObjectNode();
            response.put("jsonrpc", McpTestData.JSON_RPC_VERSION);
            response.set("id", initializeRequest.get("id"));
            var result = response.putObject("result");
            result.put("protocolVersion", McpTestData.CLASSIC_PROTOCOL_VERSION);
            result.putObject("capabilities").putObject("tools");
            result.putObject("serverInfo").put("name", McpTestData.FAKE_CLASSIC_SERVER_NAME).put("version", McpTestData.FAKE_SERVER_VERSION);
            writer.write(OBJECT_MAPPER.writeValueAsString(response));
            writer.newLine();
            writer.flush();

            JsonNode initializedNotification = OBJECT_MAPPER.readTree(reader.readLine());
            if (!McpTestData.INITIALIZED_NOTIFICATION_METHOD.equals(initializedNotification.path("method").asText())
                    || initializedNotification.has("id")) {
                System.exit(3);
            }

            // Ожидание EOF от AQA подтверждает штатное закрытие STDIN.
            while (reader.readLine() != null) {
                // No additional messages are expected in this lifecycle test.
            }
        }
    }
}
