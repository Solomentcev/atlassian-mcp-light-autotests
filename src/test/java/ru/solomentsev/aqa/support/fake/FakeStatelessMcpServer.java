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
 * Минимальный stateless MCP-процесс для проверки запуска без classic initialize.
 */
public final class FakeStatelessMcpServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FakeStatelessMcpServer() {
    }

    public static void main(String[] args) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(System.out, StandardCharsets.UTF_8))) {
            JsonNode discoveryRequest = OBJECT_MAPPER.readTree(reader.readLine());
            if (!McpTestData.SERVER_DISCOVER_METHOD.equals(discoveryRequest.path("method").asText())
                    || !McpTestData.STATELESS_PROTOCOL_VERSION.equals(discoveryRequest.path("params").path("_meta")
                    .path("io.modelcontextprotocol/protocolVersion").asText())) {
                System.exit(2);
            }
            writeResponse(writer, discoveryRequest, discoveryResult());

            JsonNode toolsRequest = OBJECT_MAPPER.readTree(reader.readLine());
            if (!McpTestData.TOOLS_LIST_METHOD.equals(toolsRequest.path("method").asText())) {
                System.exit(3);
            }
            var toolsResult = OBJECT_MAPPER.createObjectNode();
            toolsResult.put("resultType", "complete");
            toolsResult.putArray("tools");
            writeResponse(writer, toolsRequest, toolsResult);

            while (reader.readLine() != null) {
                // Wait for AQA to close STDIN.
            }
        }
    }

    private static JsonNode discoveryResult() {
        var result = OBJECT_MAPPER.createObjectNode();
        result.put("resultType", "complete");
        result.putArray("supportedVersions")
                .add(McpTestData.STATELESS_PROTOCOL_VERSION)
                .add(McpTestData.CLASSIC_PROTOCOL_VERSION)
                .add(McpTestData.LEGACY_PROTOCOL_VERSION);
        result.putObject("capabilities").putObject("tools");
        result.putObject("_meta").putObject("io.modelcontextprotocol/serverInfo")
                .put("name", McpTestData.FAKE_STATELESS_SERVER_NAME)
                .put("version", McpTestData.FAKE_SERVER_VERSION);
        result.put("ttlMs", McpTestData.DISCOVERY_TTL_MS);
        result.put("cacheScope", McpTestData.DISCOVERY_CACHE_SCOPE);
        return result;
    }

    private static void writeResponse(BufferedWriter writer,
                                      JsonNode request,
                                      JsonNode result) throws Exception {
        var response = OBJECT_MAPPER.createObjectNode();
        response.put("jsonrpc", McpTestData.JSON_RPC_VERSION);
        response.set("id", request.get("id"));
        response.set("result", result);
        writer.write(OBJECT_MAPPER.writeValueAsString(response));
        writer.newLine();
        writer.flush();
    }
}
