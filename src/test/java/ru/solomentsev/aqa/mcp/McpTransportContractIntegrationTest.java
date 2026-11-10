package ru.solomentsev.aqa.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
class McpTransportContractIntegrationTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldIgnoreBlankLinesAndContinueAfterParseError(McpClient client) {
        client.sendRawLine("   ");
        client.sendRawLine("{invalid-json");

        var parseError = client.awaitRawResponse(REQUEST_TIMEOUT);
        assertJsonRpcError(parseError, -32700);
        assertThat(parseError.has("id")).isTrue();
        assertThat(parseError.get("id").isNull()).isTrue();

        var ping = client.request("ping", null, REQUEST_TIMEOUT);
        assertThat(ping.has("error")).isFalse();
        assertThat(ping.path("result").isObject()).isTrue();
    }

    @Test
    void shouldRejectNonObjectRootAndInvalidRequestShape(McpClient client) {
        client.sendRawLine("[]");
        assertJsonRpcError(client.awaitRawResponse(REQUEST_TIMEOUT), -32600);

        client.sendRawLine("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}");
        assertJsonRpcError(client.awaitRawResponse(REQUEST_TIMEOUT), -32600);

        client.sendRawLine("{\"jsonrpc\":\"2.0\",\"id\":{},\"method\":\"ping\"}");
        assertJsonRpcError(client.awaitRawResponse(REQUEST_TIMEOUT), -32600);
    }

    @Test
    void shouldReturnMethodNotFoundForUnknownMethod(McpClient client) {
        var response = client.request("aqa/unknown-method", null, REQUEST_TIMEOUT);

        assertJsonRpcError(response, -32601);
    }

    @Test
    void shouldNotRespondToNotifications(McpClient client) {
        client.notification("notifications/initialized", null);

        var ping = client.request("ping", null, REQUEST_TIMEOUT);

        assertThat(ping.has("error")).isFalse();
        assertThat(ping.path("result").isObject()).isTrue();
    }

    @Test
    void shouldRejectSecondClassicInitialize(McpClient client) {
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("protocolVersion", "2025-11-25");
        params.putObject("capabilities");
        params.putObject("clientInfo")
                .put("name", "atlassian-mcp-light-autotests")
                .put("version", "1.0");

        var response = client.request("initialize", params, REQUEST_TIMEOUT);

        assertJsonRpcError(response, -32600);
    }

    private static void assertJsonRpcError(com.fasterxml.jackson.databind.JsonNode response,
                                           int expectedCode) {
        assertThat(response.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.has("error")).isTrue();
        assertThat(response.path("error").path("code").asInt()).isEqualTo(expectedCode);
    }
}
