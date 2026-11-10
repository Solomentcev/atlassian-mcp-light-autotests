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
    @DisplayName("Отправка запроса и сопоставление ответа по request id")
    @Description("Предусловия: Созданы тестовые STDIO-потоки и имитация MCP-сервера.\nШаги: отправить ping через MCP Client; получить ответ.\nОжидаемый результат: ответ содержит JSON-RPC 2.0, исходный request id и ожидаемый result.")
    @Tag("protocol")
    void shouldSendRequestAndMatchResponseById() throws Exception {
        PipedInputStream clientStdout = new PipedInputStream(8_192);
        PipedOutputStream serverStdout = new PipedOutputStream(clientStdout);
        PipedInputStream serverStdin = new PipedInputStream(8_192);
        PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);

        Thread server = new Thread(() -> respondToSingleRequest(serverStdin, serverStdout));
        server.setDaemon(true);
        server.start();

        try (McpClient client = new McpClient(clientStdout, clientStdin)) {
            JsonNode response = sendRequest(client, "ping", null, McpTestData.SHORT_REQUEST_TIMEOUT);

            assertThat(response.path("jsonrpc").asText()).as("Ответ должен соответствовать JSON-RPC версии 2.0").isEqualTo("2.0");
            assertThat(response.path("result").isObject()).as("Ответ на ping должен содержать объект result").isTrue();
            assertThat(response.path("result").path("alive").asBoolean()).as("Fake MCP server должен вернуть alive=true").isTrue();
        } finally {
            serverStdin.close();
            serverStdout.close();
        }
    
    
    }

    @Test
    @Feature("MCP Client")
    @Story("Отправка notification без request id")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отправка notification без request id")
    @Description("Предусловия: Созданы тестовые STDIO-потоки и имитация сервера.\nШаги: отправить notifications/initialized; проверить принятое сервером сообщение.\nОжидаемый результат: notification не содержит id и передаётся без ожидания response.")
    @Tag("protocol")
    void shouldSendNotificationWithoutRequestId() throws Exception {
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

        assertThat(notificationReceived.await(3, TimeUnit.SECONDS)).as("Fake MCP server должен получить notification в пределах timeout").isTrue();
        assertThat(received.get().path("method").asText())
                .as("Fake MCP server должен получить notifications/initialized")
                .isEqualTo("notifications/initialized");
        assertThat(received.get().has("id")).as("MCP notification не должен содержать request id").isFalse();
    } finally {
        serverStdin.close();
        serverStdout.close();
    }

    
    }

    @Test
    @Feature("MCP Client")
    @Story("Выполнение classic initialize handshake")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Выполнение classic initialize handshake")
    @Description("Предусловия: Созданы тестовые STDIO-потоки и имитация MCP-сервера.\nШаги: выполнить initialize через MCP Client; дождаться notifications/initialized.\nОжидаемый результат: версия протокола проверена; notification отправлен без request id.")
    @Tag("protocol")
    void shouldCompleteClassicInitializeHandshake() throws Exception {
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
                result.putObject("serverInfo").put("name", "atlassian-lite").put("version", "1.0.0");
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
                "atlassian-mcp-light-autotests",
                "1.0",
                McpTestData.SHORT_REQUEST_TIMEOUT
        );

        assertThat(response.path("result").path("protocolVersion").asText())
                .as("Initialize должен согласовать ожидаемую версию MCP-протокола")
                .isEqualTo(McpTestData.CLASSIC_PROTOCOL_VERSION);
        assertThat(initializedNotificationReceived.await(3, TimeUnit.SECONDS)).as("После initialize клиент должен отправить notifications/initialized").isTrue();
        assertThat(initializedNotification.get().path("method").asText()).as("Проверка initializedNotification.get().path('method').asText(): ожидалось isEqualTo('notifications/initialized')")
                .isEqualTo("notifications/initialized");
        assertThat(initializedNotification.get().has("id")).as("Проверка initializedNotification.get().has('id'): ожидалось isFalse").isFalse();
    } finally {
        serverStdin.close();
        serverStdout.close();
    }

    
    }

    @Test
    @Feature("MCP Client")
    @Story("Добавление stateless metadata без изменения исходных params")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Добавление stateless metadata без изменения исходных params")
    @Description("Предусловия: Созданы тестовые STDIO-потоки; подготовлены исходные params и client capabilities.\nШаги: выполнить stateless request; проверить переданные серверу metadata и исходный объект params.\nОжидаемый результат: protocolVersion, clientCapabilities и clientInfo добавлены в копию params; исходный объект не изменён.")
    @Tag("protocol")
    void shouldAddStatelessMetadataWithoutMutatingOriginalParams() throws Exception {
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
            .put("name", "aqa-tests")
            .put("version", "1.0");

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
        assertThat(requestReceived.await(3, TimeUnit.SECONDS)).as("Fake MCP server должен получить stateless-запрос в пределах timeout").isTrue();
        assertThat(received.get().path("params").path("filter").asText()).as("Проверка received.get().path('params').path('filter').asText(): ожидалось isEqualTo('all')").isEqualTo("all");
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/protocolVersion").asText()).as("Проверка received.get().path('params').path('_meta') .path('io.modelcontextprotocol/protocolVersion').asText(: ожидалось isEqualTo(McpTestData.STATELESS_PROTOCOL_VERSION)")
                .isEqualTo(McpTestData.STATELESS_PROTOCOL_VERSION);
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/clientCapabilities").path("experimental").asBoolean()).as("Проверка received.get().path('params').path('_meta') .path('io.modelcontextprotocol/clientCapabilities').path: ожидалось isTrue")
                .isTrue();
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/clientInfo").path("name").asText()).as("Проверка received.get().path('params').path('_meta') .path('io.modelcontextprotocol/clientInfo').path('name'): ожидалось isEqualTo('aqa-tests')")
                .isEqualTo("aqa-tests");
        assertThat(originalParams.has("_meta")).as("MCP Client не должен изменять исходный params при добавлении metadata").isFalse();
    } finally {
        serverStdin.close();
        serverStdout.close();
    }

    
    }

    @Test
    @Feature("MCP Client")
    @Story("Отклонение params с уже заданным _meta")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение params с уже заданным _meta")
    @Description("Предусловия: Переданы params с уже существующим полем _meta.\nШаги: вызвать requestStateless.\nОжидаемый результат: клиент выбрасывает IllegalArgumentException и не отправляет запрос.")
    @Tag("negative")
    void shouldRejectStatelessParamsContainingMetadata() throws Exception {
        PipedInputStream clientStdout = new PipedInputStream(1_024);
        PipedInputStream serverStdin = new PipedInputStream(1_024);
        PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);
        var params = OBJECT_MAPPER.createObjectNode();
        params.putObject("_meta");

        try (McpClient client = new McpClient(clientStdout, clientStdin)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> requestStateless(client, 
                    "tools/list",
                    params,
                    McpTestData.STATELESS_PROTOCOL_VERSION,
                    OBJECT_MAPPER.createObjectNode(),
                    null,
                    Duration.ofSeconds(1)
             )).as("Stateless request с уже существующим _meta должен быть отклонён до отправки")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("_meta");
        } finally {
            clientStdout.close();
            serverStdin.close();
        }
    
    
    }

    private static JsonNode sendRequest(McpClient client, String method, JsonNode params, Duration timeout) {
        return Allure.step("Отправить MCP-запрос " + method,
                () -> client.request(method, params, timeout));
    }

    private static void sendNotification(McpClient client, String method, JsonNode params) {
        Allure.step("Отправить MCP-уведомление " + method, () -> client.notification(method, params));
    }

    private static JsonNode initialize(McpClient client, String version, String clientName,
                                        String clientVersion, Duration timeout) {
        return Allure.step("Выполнить MCP initialize", () ->
                client.initialize(version, clientName, clientVersion, timeout));
    }

    private static JsonNode requestStateless(McpClient client, String method, JsonNode params,
                                             String version, JsonNode capabilities,
                                             JsonNode clientInfo, Duration timeout) {
        return Allure.step("Отправить stateless MCP-запрос " + method, () ->
                client.requestStateless(method, params, version, capabilities, clientInfo, timeout));
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
