package ru.solomentsev.aqa.support.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.qameta.allure.Allure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.solomentsev.aqa.mcp.McpClient;
import ru.solomentsev.aqa.testdata.McpTestData;

/** Действия и преобразования данных для MCP contract сценариев. */
public final class McpContractTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = McpTestData.getRequestTimeout();

    private McpContractTestSupport() {
    }

    public static JsonNode sendRequest(McpClient client, String method, ObjectNode params) {
        return Allure.step("Отправить JSON-RPC request: method=" + method,
                () -> client.request(method, params, REQUEST_TIMEOUT));
    }

    public static JsonNode callTool(McpClient client, String name, ObjectNode arguments) {
        return Allure.step("Вызвать MCP-инструмент через tools/call: name=" + name, () -> {
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments);
        return client.requestStateless(
                McpTestData.getToolsCallMethod(),
                params,
                McpTestData.getStatelessProtocolVersion(),
                OBJECT_MAPPER.createObjectNode(),
                null,
                REQUEST_TIMEOUT
        );
        });
    }

    public static JsonNode requestStatelessTools(McpClient client) {
        return Allure.step("Выполнить stateless tools/list",
                () -> client.listToolsStateless(
                        McpTestData.getStatelessProtocolVersion(),
                        REQUEST_TIMEOUT
                ));
    }

    public static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    public static Set<String> fieldNames(JsonNode object) {
        Set<String> names = new HashSet<>();
        object.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
