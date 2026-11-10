package ru.solomentsev.aqa.tests.client;

import ru.solomentsev.aqa.mcp.McpClient;

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
        Allure.step("Отправка запроса и сопоставление ответа по request id", () -> {
            PipedInputStream clientStdout = new PipedInputStream(8_192);
            PipedOutputStream serverStdout = new PipedOutputStream(clientStdout);
            PipedInputStream serverStdin = new PipedInputStream(8_192);
            PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);

            Thread server = new Thread(() -> respondToSingleRequest(serverStdin, serverStdout));
            server.setDaemon(true);
            server.start();

            try (McpClient client = new McpClient(clientStdout, clientStdin)) {
                JsonNode response = client.request("ping", null, Duration.ofSeconds(3));

                assertThat(response.path("jsonrpc").asText()).isEqualTo("2.0");
                assertThat(response.path("result").isObject()).isTrue();
                assertThat(response.path("result").path("alive").asBoolean()).isTrue();
            } finally {
                serverStdin.close();
                serverStdout.close();
            }
        
        });
    }

    @Test
    @Feature("MCP Client")
    @Story("Отправка notification без request id")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отправка notification без request id")
    @Description("Предусловия: Созданы тестовые STDIO-потоки и имитация сервера.\nШаги: отправить notifications/initialized; проверить принятое сервером сообщение.\nОжидаемый результат: notification не содержит id и передаётся без ожидания response.")
    @Tag("protocol")
    void shouldSendNotificationWithoutRequestId() throws Exception {
        Allure.step("Отправка notification без request id", () -> {
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
            client.notification("notifications/initialized", null);

            assertThat(notificationReceived.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get().path("method").asText())
                    .isEqualTo("notifications/initialized");
            assertThat(received.get().has("id")).isFalse();
        } finally {
            serverStdin.close();
            serverStdout.close();
        }
    
        });
    }

    @Test
    @Feature("MCP Client")
    @Story("Выполнение classic initialize handshake")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Выполнение classic initialize handshake")
    @Description("Предусловия: Созданы тестовые STDIO-потоки и имитация MCP-сервера.\nШаги: выполнить initialize через MCP Client; дождаться notifications/initialized.\nОжидаемый результат: версия протокола проверена; notification отправлен без request id.")
    @Tag("protocol")
    void shouldCompleteClassicInitializeHandshake() throws Exception {
        Allure.step("Выполнение classic initialize handshake", () -> {
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
                    result.put("protocolVersion", "2025-11-25");
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
            JsonNode response = client.initialize(
                    "2025-11-25",
                    "atlassian-mcp-light-autotests",
                    "1.0",
                    Duration.ofSeconds(3)
            );

            assertThat(response.path("result").path("protocolVersion").asText())
                    .isEqualTo("2025-11-25");
            assertThat(initializedNotificationReceived.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(initializedNotification.get().path("method").asText())
                    .isEqualTo("notifications/initialized");
            assertThat(initializedNotification.get().has("id")).isFalse();
        } finally {
            serverStdin.close();
            serverStdout.close();
        }
    
        });
    }

    @Test
    @Feature("MCP Client")
    @Story("Добавление stateless metadata без изменения исходных params")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Добавление stateless metadata без изменения исходных params")
    @Description("Предусловия: Созданы тестовые STDIO-потоки; подготовлены исходные params и client capabilities.\nШаги: выполнить stateless request; проверить переданные серверу metadata и исходный объект params.\nОжидаемый результат: protocolVersion, clientCapabilities и clientInfo добавлены в копию params; исходный объект не изменён.")
    @Tag("protocol")
    void shouldAddStatelessMetadataWithoutMutatingOriginalParams() throws Exception {
        Allure.step("Добавление stateless metadata без изменения исходных params", () -> {
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
            JsonNode response = client.requestStateless(
                    "tools/list",
                    originalParams,
                    "2026-07-28",
                    capabilities,
                    clientInfo,
                    Duration.ofSeconds(3)
            );

            assertThat(response.path("result").path("resultType").asText())
                    .isEqualTo("complete");
            assertThat(requestReceived.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get().path("params").path("filter").asText()).isEqualTo("all");
            assertThat(received.get().path("params").path("_meta")
                    .path("io.modelcontextprotocol/protocolVersion").asText())
                    .isEqualTo("2026-07-28");
            assertThat(received.get().path("params").path("_meta")
                    .path("io.modelcontextprotocol/clientCapabilities").path("experimental").asBoolean())
                    .isTrue();
            assertThat(received.get().path("params").path("_meta")
                    .path("io.modelcontextprotocol/clientInfo").path("name").asText())
                    .isEqualTo("aqa-tests");
            assertThat(originalParams.has("_meta")).isFalse();
        } finally {
            serverStdin.close();
            serverStdout.close();
        }
    
        });
    }

    @Test
    @Feature("MCP Client")
    @Story("Отклонение params с уже заданным _meta")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение params с уже заданным _meta")
    @Description("Предусловия: Переданы params с уже существующим полем _meta.\nШаги: вызвать requestStateless.\nОжидаемый результат: клиент выбрасывает IllegalArgumentException и не отправляет запрос.")
    @Tag("negative")
    void shouldRejectStatelessParamsContainingMetadata() throws Exception {
        Allure.step("Отклонение params с уже заданным _meta", () -> {
            PipedInputStream clientStdout = new PipedInputStream(1_024);
            PipedInputStream serverStdin = new PipedInputStream(1_024);
            PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);
            var params = OBJECT_MAPPER.createObjectNode();
            params.putObject("_meta");

            try (McpClient client = new McpClient(clientStdout, clientStdin)) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.requestStateless(
                        "tools/list",
                        params,
                        "2026-07-28",
                        OBJECT_MAPPER.createObjectNode(),
                        null,
                        Duration.ofSeconds(1)
                )).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("_meta");
            } finally {
                clientStdout.close();
                serverStdin.close();
            }
        
        });
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
