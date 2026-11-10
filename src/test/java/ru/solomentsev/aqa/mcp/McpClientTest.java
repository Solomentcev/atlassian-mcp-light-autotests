package ru.solomentsev.aqa.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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

class McpClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldSendRequestAndMatchResponseById() throws Exception {
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
    }

    @Test
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
            client.notification("notifications/initialized", null);

            assertThat(notificationReceived.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(received.get().path("method").asText())
                    .isEqualTo("notifications/initialized");
            assertThat(received.get().has("id")).isFalse();
        } finally {
            serverStdin.close();
            serverStdout.close();
        }
    }

    @Test
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
    }

    @Test
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
    }

    @Test
    void shouldRejectStatelessParamsContainingMetadata() throws Exception {
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
