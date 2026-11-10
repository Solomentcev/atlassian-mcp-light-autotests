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
