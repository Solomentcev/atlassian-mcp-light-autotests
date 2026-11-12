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
            if (!McpTestData.getServerDiscoverMethod().equals(discoveryRequest.path("method").asText())
                    || !McpTestData.getStatelessProtocolVersion().equals(discoveryRequest.path("params").path("_meta")
                    .path("io.modelcontextprotocol/protocolVersion").asText())) {
                System.exit(2);
            }
            writeResponse(writer, discoveryRequest, discoveryResult());

            JsonNode toolsRequest = OBJECT_MAPPER.readTree(reader.readLine());
            if (!McpTestData.getToolsListMethod().equals(toolsRequest.path("method").asText())) {
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
                .add(McpTestData.getStatelessProtocolVersion())
                .add(McpTestData.getClassicProtocolVersion())
                .add(McpTestData.getLegacyProtocolVersion());
        result.putObject("capabilities").putObject("tools");
        result.putObject("_meta").putObject("io.modelcontextprotocol/serverInfo")
                .put("name", McpTestData.getFakeStatelessServerName())
                .put("version", McpTestData.getFakeServerVersion());
        result.put("ttlMs", McpTestData.getDiscoveryTtlMs());
        result.put("cacheScope", McpTestData.getDiscoveryCacheScope());
        return result;
    }

    private static void writeResponse(BufferedWriter writer,
                                      JsonNode request,
                                      JsonNode result) throws Exception {
        var response = OBJECT_MAPPER.createObjectNode();
        response.put("jsonrpc", McpTestData.getJsonRpcVersion());
        response.set("id", request.get("id"));
        response.set("result", result);
        writer.write(OBJECT_MAPPER.writeValueAsString(response));
        writer.newLine();
        writer.flush();
    }
}
