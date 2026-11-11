package ru.solomentsev.aqa.tests.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ru.solomentsev.aqa.mcp.McpClient;
import ru.solomentsev.aqa.testdata.McpTestData;

import static ru.solomentsev.aqa.mcp.McpClientTestActions.initialize;
import static ru.solomentsev.aqa.mcp.McpClientTestActions.requestStateless;
import static ru.solomentsev.aqa.mcp.McpClientTestActions.respondToSingleRequest;
import static ru.solomentsev.aqa.mcp.McpClientTestActions.sendNotification;
import static ru.solomentsev.aqa.mcp.McpClientTestActions.sendRequest;
import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThat;

@Epic("AQA Test Infrastructure")
@Tag("unit")
@Tag("mcp-client")
@Tag("regression")
class McpClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @Feature("MCP Client")
    @Story("Отправка запроса и сопоставление ответа по request id")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отправка ping и сопоставление ответа по request id")
    @Description("Предусловия: MCP Client подключён к имитации сервера через отдельные STDIN/STDOUT-потоки.\nШаги: отправить JSON-RPC request с method=ping; дождаться ответа в пределах настроенного timeout.\nОжидаемый результат: ответ имеет jsonrpc=2.0, request id совпадает с отправленным, result является объектом и содержит alive=true.")
    @Tag("protocol")
    void shouldSendRequestAndMatchResponseById() throws Exception {
        Allure.parameter("JSON-RPC method", McpTestData.getPingMethod());
        Allure.parameter("Request timeout", McpTestData.getShortRequestTimeout().toMillis() + " ms");
        PipedInputStream clientStdout = new PipedInputStream(McpTestData.getPipeBufferSizeBytes());
        PipedOutputStream serverStdout = new PipedOutputStream(clientStdout);
        PipedInputStream serverStdin = new PipedInputStream(McpTestData.getPipeBufferSizeBytes());
        PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);

        Thread server = new Thread(() -> respondToSingleRequest(serverStdin, serverStdout));
        server.setDaemon(true);
        server.start();

        try (McpClient client = new McpClient(clientStdout, clientStdin)) {
            JsonNode response = sendRequest(client, McpTestData.getPingMethod(), null, McpTestData.getShortRequestTimeout());

            assertThat(response.path("jsonrpc").asText()).as("Для method=ping ожидается jsonrpc=2.0, фактически: %s", response.path("jsonrpc").asText()).isEqualTo(McpTestData.getJsonRpcVersion());
            assertThat(response.path("result").isObject()).as("Ответ на ping должен содержать объект result").isTrue();
            assertThat(response.path("result").has("alive"))
                    .as("Ответ на ping должен содержать поле result.alive")
                    .isTrue();
            assertThat(response.path("result").path("alive").isBoolean())
                    .as("Поле result.alive должно иметь boolean type")
                    .isTrue();
            assertThat(response.path("result").path("alive").asBoolean())
                    .as("Ответ на ping должен содержать result.alive=true")
                    .isTrue();
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
    @Description("Предусловия: MCP Client подключён к имитации сервера через STDIO-потоки.\nШаги: отправить notification method=notifications/initialized без request id; дождаться получения строки сервером в пределах настроенного timeout.\nОжидаемый результат: сервер получает method=notifications/initialized; поле id отсутствует, клиент не ожидает JSON-RPC response.")
    @Tag("protocol")
    void shouldSendNotificationWithoutRequestId() throws Exception {
        Allure.parameter("Notification method", McpTestData.getInitializedNotificationMethod());
        Allure.parameter("Request id", "отсутствует");
        Allure.parameter("Ожидание получения", McpTestData.getAsyncEventTimeout().toMillis() + " ms");
        PipedInputStream clientStdout = new PipedInputStream(McpTestData.getPipeBufferSizeBytes());
        PipedOutputStream serverStdout = new PipedOutputStream(clientStdout);
        PipedInputStream serverStdin = new PipedInputStream(McpTestData.getPipeBufferSizeBytes());
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
        sendNotification(client, McpTestData.getInitializedNotificationMethod(), null);

        assertThat(notificationReceived.await(McpTestData.getAsyncEventTimeout().toMillis(), TimeUnit.MILLISECONDS)).as("Fake MCP server должен получить notification в пределах timeout").isTrue();
        assertThat(received.get().path("method").asText())
                .as("Fake MCP server должен получить notifications/initialized")
                .isEqualTo(McpTestData.getInitializedNotificationMethod());
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
    @DisplayName("Classic initialize handshake")
    @Description("Предусловия: MCP Client подключён к имитации сервера, которая отвечает на initialize согласованной версией протокола.\nШаги: отправить initialize с согласованными protocolVersion и clientInfo; принять ответ; дождаться следующего сообщения клиента.\nОжидаемый результат: ответ initialize содержит согласованную protocolVersion; клиент отправляет notifications/initialized без поля id.")
    @Tag("protocol")
    void shouldCompleteClassicInitializeHandshake() throws Exception {
        Allure.parameter("MCP protocolVersion", McpTestData.getClassicProtocolVersion());
        Allure.parameter("Client name", McpTestData.getClientName());
        Allure.parameter("Client version", McpTestData.getClientVersion());
        Allure.parameter("Request timeout", McpTestData.getShortRequestTimeout().toMillis() + " ms");
        PipedInputStream clientStdout = new PipedInputStream(McpTestData.getPipeBufferSizeBytes());
        PipedOutputStream serverStdout = new PipedOutputStream(clientStdout);
        PipedInputStream serverStdin = new PipedInputStream(McpTestData.getPipeBufferSizeBytes());
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
                response.put("jsonrpc", McpTestData.getJsonRpcVersion());
                response.set("id", initializeRequest.get("id"));
                var result = response.putObject("result");
                result.put("protocolVersion", McpTestData.getClassicProtocolVersion());
                result.putObject("capabilities").putObject("tools");
                result.putObject("serverInfo").put("name", McpTestData.getServerName()).put("version", McpTestData.getServerVersion());
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
                McpTestData.getClassicProtocolVersion(),
                McpTestData.getClientName(),
                McpTestData.getClientVersion(),
                McpTestData.getShortRequestTimeout()
        );

        assertThat(response.path("result").path("protocolVersion").asText())
                .as("Initialize должен согласовать ожидаемую версию MCP-протокола")
                .isEqualTo(McpTestData.getClassicProtocolVersion());
        assertThat(initializedNotificationReceived.await(McpTestData.getAsyncEventTimeout().toMillis(), TimeUnit.MILLISECONDS)).as("После initialize клиент должен отправить notifications/initialized").isTrue();
        assertThat(initializedNotification.get().path("method").asText())
                .as("После initialize клиент должен отправить notifications/initialized")
                .isEqualTo(McpTestData.getInitializedNotificationMethod());
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
    @DisplayName("tools/list stateless metadata без изменения исходного params")
    @Description("Предусловия: MCP Client подключён к имитации сервера; исходный params содержит filter=all, capabilities содержат experimental=true, clientInfo задан.\nШаги: отправить stateless tools/list с поддерживаемой protocolVersion; проверить полученную сервером строку запроса и исходный объект params.\nОжидаемый результат: в переданном запросе сохранён filter=all и добавлены protocolVersion, clientCapabilities и clientInfo; исходный params не получает поле _meta и остаётся неизменным.")
    @Tag("protocol")
    void shouldAddStatelessMetadataWithoutMutatingOriginalParams() throws Exception {
        Allure.parameter("JSON-RPC method", McpTestData.getToolsListMethod());
        Allure.parameter("MCP protocolVersion", McpTestData.getStatelessProtocolVersion());
        Allure.parameter("Исходный params.filter", "all");
        Allure.parameter("clientCapabilities.experimental", true);
        Allure.parameter("clientInfo.name", McpTestData.getMockClientName());
        Allure.parameter("clientInfo.version", McpTestData.getMockClientVersion());
        PipedInputStream clientStdout = new PipedInputStream(McpTestData.getPipeBufferSizeBytes());
        PipedOutputStream serverStdout = new PipedOutputStream(clientStdout);
        PipedInputStream serverStdin = new PipedInputStream(McpTestData.getPipeBufferSizeBytes());
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
                response.put("jsonrpc", McpTestData.getJsonRpcVersion());
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
            .put("name", McpTestData.getMockClientName())
            .put("version", McpTestData.getMockClientVersion());

    try (McpClient client = new McpClient(clientStdout, clientStdin)) {
        JsonNode response = requestStateless(client,
                McpTestData.getToolsListMethod(),
                originalParams,
                McpTestData.getStatelessProtocolVersion(),
                capabilities,
                clientInfo,
                McpTestData.getShortRequestTimeout()
        );

        assertThat(response.path("result").path("resultType").asText())
                .as("Stateless MCP-ответ должен иметь resultType=complete")
                .isEqualTo("complete");
        assertThat(requestReceived.await(McpTestData.getAsyncEventTimeout().toMillis(), TimeUnit.MILLISECONDS)).as("Fake MCP server должен получить stateless-запрос в пределах timeout").isTrue();
        assertThat(received.get().path("params").path("filter").asText())
                .as("Исходный параметр filter должен быть передан серверу без изменения").isEqualTo("all");
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/protocolVersion").asText())
                .as("В stateless metadata должна передаваться согласованная версия MCP-протокола")
                .isEqualTo(McpTestData.getStatelessProtocolVersion());
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/clientCapabilities").has("experimental"))
                .as("В stateless metadata должен присутствовать clientCapabilities.experimental")
                .isTrue();
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/clientCapabilities").path("experimental").isBoolean())
                .as("clientCapabilities.experimental должен иметь boolean type")
                .isTrue();
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/clientCapabilities").path("experimental").asBoolean())
                .as("В stateless metadata должны передаваться client capabilities")
                .isTrue();
        assertThat(received.get().path("params").path("_meta")
                 .path("io.modelcontextprotocol/clientInfo").path("name").asText())
                .as("В stateless metadata должно передаваться имя MCP-клиента")
                .isEqualTo(McpTestData.getMockClientName());
        assertThat(received.get().path("params").path("_meta")
                .path("io.modelcontextprotocol/clientInfo").path("version").asText())
                .as("В stateless metadata должна передаваться версия MCP-клиента")
                .isEqualTo(McpTestData.getMockClientVersion());
        assertThat(originalParams.path("filter").asText())
                .as("Исходный params.filter должен остаться равным all после отправки запроса")
                .isEqualTo("all");
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
    @DisplayName("Отклонение stateless tools/list при уже заданном params._meta")
    @Description("Предусловия: Исходный params уже содержит поле _meta; MCP Client подключён к STDIO-потокам без запущенного обработчика сервера.\nШаги: вызвать requestStateless для method=tools/list с заданной protocolVersion.\nОжидаемый результат: метод завершается IllegalArgumentException с упоминанием _meta до записи каких-либо данных в STDIN.")
    @Tag("negative")
    void shouldRejectStatelessParamsContainingMetadata() throws Exception {
        Allure.parameter("JSON-RPC method", McpTestData.getToolsListMethod());
        Allure.parameter("MCP protocolVersion", McpTestData.getStatelessProtocolVersion());
        Allure.parameter("Исходный params._meta", "уже присутствует");
        PipedInputStream clientStdout = new PipedInputStream(1_024);
        PipedInputStream serverStdin = new PipedInputStream(1_024);
        PipedOutputStream clientStdin = new PipedOutputStream(serverStdin);
        var params = OBJECT_MAPPER.createObjectNode();
        params.putObject("_meta");

        try (McpClient client = new McpClient(clientStdout, clientStdin)) {
            ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThatThrownBy(() -> Allure.step(
                    "Выполнить stateless tools/list с уже существующим params._meta и protocolVersion="
                            + McpTestData.getStatelessProtocolVersion(),
                    () -> client.requestStateless(
                            McpTestData.getToolsListMethod(),
                            params,
                            McpTestData.getStatelessProtocolVersion(),
                            OBJECT_MAPPER.createObjectNode(),
                            null,
                            McpTestData.getInvalidRequestTimeout()
                    ))).as("Stateless tools/list с уже существующим params._meta должен быть отклонён до отправки")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("_meta");
            assertThat(serverStdin.available())
                    .as("При наличии params._meta MCP Client не должен записывать JSON-RPC запрос в STDIN")
                    .isZero();
        } finally {
            clientStdout.close();
            serverStdin.close();
        }

    }

}
