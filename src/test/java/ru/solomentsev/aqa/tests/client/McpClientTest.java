package ru.solomentsev.aqa.tests.client;

import ru.solomentsev.aqa.mcp.McpClient;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("AQA Test Infrastructure")
@Tag("unit")
@Tag("mcp-client")
class McpClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Feature("MCP Client")
    @Story("Отправка запроса и сопоставление ответа по request id")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отправка ping и сопоставление ответа по request id [method=ping, timeout=" + McpTestData.SHORT_REQUEST_TIMEOUT.toMillis() + " ms]")
    @Description("Предусловия: MCP Client подключён к имитации сервера через отдельные STDIN/STDOUT-потоки.\nШаги: отправить JSON-RPC request с method=ping; дождаться ответа в пределах " + McpTestData.SHORT_REQUEST_TIMEOUT.toMillis() + " ms.\nОжидаемый результат: ответ имеет jsonrpc=2.0, request id совпадает с отправленным, result является объектом и содержит alive=true.")
    @Tag("protocol")
    void shouldSendRequestAndMatchResponseById() throws Exception {
        Allure.parameter("JSON-RPC method", "ping");
        Allure.parameter("Request timeout", McpTestData.SHORT_REQUEST_TIMEOUT.toMillis() + " ms");
        PipedInputStream clientStdout = new PipedInputStream(8_192);
        PipedOutputStream serverStdout = new PipedOutputStream(clientStdout);
        PipedInputStream serverStdin = new PipedInputStream(8_192);
        PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);

        Thread server = new Thread(() -> respondToSingleRequest(serverStdin, serverStdout));
        server.setDaemon(true);
        server.start();

        try (McpClient client = new McpClient(clientStdout, clientStdin)) {
            JsonNode response = sendRequest(client, "ping", null, McpTestData.SHORT_REQUEST_TIMEOUT);

            assertThat(response.path("jsonrpc").asText()).as("Для method=ping ожидается jsonrpc=2.0, фактически: %s", response.path("jsonrpc").asText()).isEqualTo("2.0");
            assertThat(response.path("result").isObject()).as("Ответ на ping должен содержать объект result").isTrue();
            assertThat(response.path("result").path("alive").asBoolean()).as("Ответ на ping должен содержать result.alive=true, фактически: %s", response.path("result").path("alive")).isTrue();
        } finally {
            serverStdin.close();
            serverStdout.close();
        }
    
    
    }

    @Test
    @Feature("MCP Client")
    @Story("Отправка notification без request id")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Передача notification notifications/initialized без request id")
    @Description("Предусловия: MCP Client подключён к имитации сервера через STDIO-потоки.\nШаги: отправить notification method=notifications/initialized без request id; дождаться получения строки сервером не более " + McpTestData.ASYNC_EVENT_TIMEOUT.toMillis() + " ms.\nОжидаемый результат: сервер получает method=notifications/initialized; поле id отсутствует, клиент не ожидает JSON-RPC response.")
    @Tag("protocol")
    void shouldSendNotificationWithoutRequestId() throws Exception {
        Allure.parameter("Notification method", "notifications/initialized");
        Allure.parameter("Request id", "отсутствует");
        Allure.parameter("Ожидание получения", McpTestData.ASYNC_EVENT_TIMEOUT.toMillis() + " ms");
        PipedInputStream clientStdout = new PipedInputStream(8_192);
        PipedOutputStream serverStdout = new PipedOutputStream(clientStdout);
        PipedInputStream serverStdin = new PipedInputStream(8_192);
        PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);
        CountDownLatch notificationReceived = new CountDownLatch(1);
        AtomicReference<JsonNode> received = new AtomicReference<>();

        Thread server = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverStdin, StandardCharsets.UTF_8))) {
                received.set(OBJECT_MAPPER.readTree(reader.readLine()));
                notificationReceived.countDown();
            } catch (Exception ignored) {
                // The assertion below reports a missing notification.
            }
    });
    server.setDaemon(true);
    server.start();

    try (McpClient client = new McpClient(clientStdout, clientStdin)) {
        sendNotification(client, "notifications/initialized", null);

        assertThat(notificationReceived.await(McpTestData.ASYNC_EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).as("Fake MCP server должен получить notification в пределах timeout").isTrue();
        assertThat(received.get().path("method").asText())
                .as("Fake MCP server должен получить notifications/initialized")
                .isEqualTo("notifications/initialized");
        assertThat(received.get().has("id")).as("Notification method=notifications/initialized не должен содержать поле id").isFalse();
    } finally {
        serverStdin.close();
        serverStdout.close();
    }

    
    }

    @Test
    @Feature("MCP Client")
    @Story("Выполнение classic initialize handshake")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Classic initialize handshake [protocolVersion=" + McpTestData.CLASSIC_PROTOCOL_VERSION + ", client=" + McpTestData.CLIENT_NAME + "/" + McpTestData.CLIENT_VERSION + "]")
    @Description("Предусловия: MCP Client подключён к имитации сервера, которая отвечает на initialize согласованной версией протокола.\nШаги: отправить initialize с protocolVersion=" + McpTestData.CLASSIC_PROTOCOL_VERSION + ", clientInfo.name=" + McpTestData.CLIENT_NAME + " и clientInfo.version=" + McpTestData.CLIENT_VERSION + "; принять ответ; дождаться следующего сообщения клиента.\nОжидаемый результат: ответ initialize содержит согласованную protocolVersion; клиент отправляет notifications/initialized без поля id.")
    @Tag("protocol")
    void shouldCompleteClassicInitializeHandshake() throws Exception {
        Allure.parameter("MCP protocolVersion", McpTestData.CLASSIC_PROTOCOL_VERSION);
        Allure.parameter("Client name", McpTestData.CLIENT_NAME);
        Allure.parameter("Client version", McpTestData.CLIENT_VERSION);
        Allure.parameter("Request timeout", McpTestData.SHORT_REQUEST_TIMEOUT.toMillis() + " ms");
        PipedInputStream clientStdout = new PipedInputStream(8_192);
        PipedOutputStream serverStdout = new PipedOutputStream(clientStdout);
        PipedInputStream serverStdin = new PipedInputStream(8_192);
        PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);
        CountDownLatch initializedNotificationReceived = new CountDownLatch(1);
        AtomicReference<JsonNode> initializedNotification = new AtomicReference<>();

        Thread server = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverStdin, StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(serverStdout, StandardCharsets.UTF_8))) {
                JsonNode initializeRequest = OBJECT_MAPPER.readTree(reader.readLine());
                var response = OBJECT_MAPPER.createObjectNode();
                response.put("jsonrpc", "2.0");
                response.set("id", initializeRequest.get("id"));
                var result = response.putObject("result");
                result.put("protocolVersion", McpTestData.CLASSIC_PROTOCOL_VERSION);
                result.putObject("capabilities").putObject("tools");
                result.putObject("serverInfo").put("name", McpTestData.SERVER_NAME).put("version", McpTestData.SERVER_VERSION);
                writer.write(OBJECT_MAPPER.writeValueAsString(response));
                writer.newLine();
                writer.flush();

                initializedNotification.set(OBJECT_MAPPER.readTree(reader.readLine()));
                initializedNotificationReceived.countDown();
            } catch (Exception ignored) {
                // The assertions below report a failed handshake.
            }
    });
    server.setDaemon(true);
    server.start();

    try (McpClient client = new McpClient(clientStdout, clientStdin)) {
        JsonNode response = initialize(client, 
                McpTestData.CLASSIC_PROTOCOL_VERSION,
                McpTestData.CLIENT_NAME,
                McpTestData.CLIENT_VERSION,
                McpTestData.SHORT_REQUEST_TIMEOUT
        );

        assertThat(response.path("result").path("protocolVersion").asText())
                .as("Initialize должен согласовать ожидаемую версию MCP-протокола")
                .isEqualTo(McpTestData.CLASSIC_PROTOCOL_VERSION);
        assertThat(initializedNotificationReceived.await(McpTestData.ASYNC_EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).as("После initialize клиент должен отправить notifications/initialized").isTrue();
        assertThat(initializedNotification.get().path("method").asText())
                .as("После initialize клиент должен отправить notifications/initialized")
                .isEqualTo("notifications/initialized");
        assertThat(initializedNotification.get().has("id"))
                .as("Notification initialized не должен содержать request id").isFalse();
    } finally {
        serverStdin.close();
        serverStdout.close();
    }

    
    }

    @Test
    @Feature("MCP Client")
    @Story("Добавление stateless metadata без изменения исходных params")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("tools/list stateless metadata [protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + ", исходный params.filter=all]")
    @Description("Предусловия: MCP Client подключён к имитации сервера; исходный params содержит filter=all, capabilities содержат experimental=true, clientInfo задан.\nШаги: отправить stateless tools/list с protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + "; проверить полученную сервером строку запроса и исходный объект params.\nОжидаемый результат: в переданном запросе сохранён filter=all и добавлены protocolVersion, clientCapabilities и clientInfo; исходный params не получает поле _meta и остаётся неизменным.")
    @Tag("protocol")
    void shouldAddStatelessMetadataWithoutMutatingOriginalParams() throws Exception {
        Allure.parameter("JSON-RPC method", "tools/list");
        Allure.parameter("MCP protocolVersion", McpTestData.STATELESS_PROTOCOL_VERSION);
        Allure.parameter("Исходный params.filter", "all");
        Allure.parameter("clientCapabilities.experimental", true);
        Allure.parameter("clientInfo.name", McpTestData.MOCK_CLIENT_NAME);
        Allure.parameter("clientInfo.version", McpTestData.MOCK_CLIENT_VERSION);
        PipedInputStream clientStdout = new PipedInputStream(8_192);
        PipedOutputStream serverStdout = new PipedOutputStream(clientStdout);
        PipedInputStream serverStdin = new PipedInputStream(8_192);
        PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);
        AtomicReference<JsonNode> received = new AtomicReference<>();
        CountDownLatch requestReceived = new CountDownLatch(1);

        Thread server = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(serverStdin, StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(serverStdout, StandardCharsets.UTF_8))) {
                received.set(OBJECT_MAPPER.readTree(reader.readLine()));
                requestReceived.countDown();

                var response = OBJECT_MAPPER.createObjectNode();
                response.put("jsonrpc", "2.0");
                response.set("id", received.get().get("id"));
                response.putObject("result").put("resultType", "complete");
                writer.write(OBJECT_MAPPER.writeValueAsString(response));
                writer.newLine();
                writer.flush();
            } catch (Exception ignored) {
                // The assertions below report a failed exchange.
            }
    });
    server.setDaemon(true);
    server.start();

    var originalParams = OBJECT_MAPPER.createObjectNode().put("filter", "all");
    var capabilities = OBJECT_MAPPER.createObjectNode().put("experimental", true);
    var clientInfo = OBJECT_MAPPER.createObjectNode()
            .put("name", McpTestData.MOCK_CLIENT_NAME)
            .put("version", McpTestData.MOCK_CLIENT_VERSION);

    try (McpClient client = new McpClient(clientStdout, clientStdin)) {
        JsonNode response = requestStateless(client, 
                "tools/list",
                originalParams,
                McpTestData.STATELESS_PROTOCOL_VERSION,
                capabilities,
                clientInfo,
                McpTestData.SHORT_REQUEST_TIMEOUT
        );

        assertThat(response.path("result").path("resultType").asText())
                .as("Stateless MCP-ответ должен иметь resultType=complete")
                .isEqualTo("complete");
        assertThat(requestReceived.await(McpTestData.ASYNC_EVENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).as("Fake MCP server должен получить stateless-запрос в пределах timeout").isTrue();
        assertThat(received.get().path("params").path("filter").asText())
                .as("Исходный параметр filter должен быть передан серверу без изменения").isEqualTo("all");
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/protocolVersion").asText())
                .as("В stateless metadata должна передаваться согласованная версия MCP-протокола")
                .isEqualTo(McpTestData.STATELESS_PROTOCOL_VERSION);
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/clientCapabilities").path("experimental").asBoolean())
                .as("В stateless metadata должны передаваться client capabilities")
                .isTrue();
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/clientInfo").path("name").asText())
                .as("В stateless metadata должно передаваться имя MCP-клиента")
                .isEqualTo("aqa-tests");
        assertThat(originalParams.has("_meta")).as("После stateless tools/list исходный params.filter=all не должен получить поле _meta").isFalse();
    } finally {
        serverStdin.close();
        serverStdout.close();
    }

    
    }

    @Test
    @Feature("MCP Client")
    @Story("Отклонение params с уже заданным _meta")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение stateless tools/list при уже заданном params._meta [protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + "]")
    @Description("Предусловия: Исходный params уже содержит поле _meta; MCP Client подключён к STDIO-потокам без запущенного обработчика сервера.\nШаги: вызвать requestStateless для method=tools/list с protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + ".\nОжидаемый результат: метод завершается IllegalArgumentException с упоминанием _meta до записи каких-либо данных в STDIN.")
    @Tag("negative")
    void shouldRejectStatelessParamsContainingMetadata() throws Exception {
        Allure.parameter("JSON-RPC method", "tools/list");
        Allure.parameter("MCP protocolVersion", McpTestData.STATELESS_PROTOCOL_VERSION);
        Allure.parameter("Исходный params._meta", "уже присутствует");
        PipedInputStream clientStdout = new PipedInputStream(1_024);
        PipedInputStream serverStdin = new PipedInputStream(1_024);
        PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);
        var params = OBJECT_MAPPER.createObjectNode();
        params.putObject("_meta");

        try (McpClient client = new McpClient(clientStdout, clientStdin)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> Allure.step(
                    "Выполнить stateless tools/list с уже существующим params._meta и protocolVersion="
                            + McpTestData.STATELESS_PROTOCOL_VERSION,
                    () -> client.requestStateless(
                            "tools/list",
                            params,
                            McpTestData.STATELESS_PROTOCOL_VERSION,
                            OBJECT_MAPPER.createObjectNode(),
                            null,
                            McpTestData.INVALID_REQUEST_TIMEOUT
                    ))).as("Stateless tools/list с уже существующим params._meta должен быть отклонён до отправки")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("_meta");
        } finally {
            clientStdout.close();
            serverStdin.close();
        }
    
    
    }

    private static JsonNode sendRequest(McpClient client, String method, ObjectNode params, Duration timeout) {
        return Allure.step("Отправить JSON-RPC request: method=" + method + ", timeout=" + timeout.toMillis() + " ms", () -> client.request(method, params, timeout));
    }

    private static void sendNotification(McpClient client, String method, ObjectNode params) {
        Allure.step("Отправить JSON-RPC notification: method=" + method + ", request id отсутствует", () -> client.notification(method, params));
    }

    private static JsonNode initialize(McpClient client, String version, String clientName,
                                        String clientVersion, Duration timeout) {
        return Allure.step("Выполнить initialize: protocolVersion=" + version + ", client=" + clientName + "/" + clientVersion, () -> client.initialize(version, clientName, clientVersion, timeout));
    }

    private static JsonNode requestStateless(McpClient client, String method, ObjectNode params,
                                             String version, ObjectNode capabilities,
                                             ObjectNode clientInfo, Duration timeout) {
        return Allure.step("Отправить stateless request: method=" + method + ", protocolVersion=" + version + ", params fields=" + (params == null ? "отсутствуют" : "полей=" + params.size()), () -> client.requestStateless(method, params, version, capabilities, clientInfo, timeout));
    }

    private static void respondToSingleRequest(PipedInputStream serverStdin,
                                               PipedOutputStream serverStdout) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(serverStdin, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(serverStdout, StandardCharsets.UTF_8))) {
            JsonNode request = OBJECT_MAPPER.readTree(reader.readLine());
            var response = OBJECT_MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.set("id", request.get("id"));
            response.putObject("result").put("alive", true);
            writer.write(OBJECT_MAPPER.writeValueAsString(response));
            writer.newLine();
            writer.flush();
        } catch (Exception ignored) {
            // The client-side assertion reports a failed exchange.
        }
    }
}
