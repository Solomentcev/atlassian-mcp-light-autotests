package ru.solomentsev.aqa.mcp;

import io.qameta.allure.Allure;
import ru.solomentsev.aqa.testdata.McpTestData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Минимальный синхронный MCP/JSON-RPC клиент для одного STDIO-процесса.
 *
 * <p>Клиент отправляет по одному JSON-RPC сообщению на строку и читает ответы
 * только из STDOUT. STDERR должен обслуживаться отдельным {@code StderrCollector}.</p>
 *
 * <p>Один экземпляр клиента обслуживает одну MCP-сессию. Параллельные запросы
 * через один экземпляр не поддерживаются.</p>
 */
public final class McpClient implements AutoCloseable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BufferedWriter writer;
    private final BlockingQueue<ReadEvent> responses = new LinkedBlockingQueue<>();
    private final AtomicLong requestIds = new AtomicLong();
    private final Thread readerThread;

    private volatile boolean closed;
    private volatile boolean failed;

    /**
     * Создаёт клиента и запускает отдельный поток чтения STDOUT.
     *
     * @param stdout STDOUT тестируемого процесса
     * @param stdin STDIN тестируемого процесса
     */
    public McpClient(InputStream stdout, OutputStream stdin) {
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stdin, "stdin");
        this.writer = new BufferedWriter(new OutputStreamWriter(stdin, StandardCharsets.UTF_8));
        this.readerThread = new Thread(() -> readResponses(stdout), "aqa-mcp-stdout-reader");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    private void readResponses(InputStream stdout) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stdout, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responses.put(ReadEvent.line(line));
            }
            responses.put(ReadEvent.endOfStream());
        } catch (IOException exception) {
            responses.offer(ReadEvent.failure(exception));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            responses.offer(ReadEvent.failure(exception));
        }
    }

    /**
     * Отправляет JSON-RPC request и ожидает соответствующий response.
     *
     * @param method имя метода
     * @param params объект параметров; {@code null} означает отсутствие params
     * @param timeout таймаут ожидания ответа
     * @return полный JSON-RPC response
     */
    public synchronized JsonNode request(String method, ObjectNode params, Duration timeout) {
        ensureUsable();
        validateMethod(method);
        validateTimeout(timeout);

        long requestId = requestIds.incrementAndGet();
        ObjectNode request = OBJECT_MAPPER.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId);
        request.put("method", method);
        if (params != null) {
            request.set("params", params);
        }

        Allure.step("Отправить JSON-RPC request: " + method, () -> writeMessage(request));
        Allure.addAttachment(
                "JSON-RPC request metadata",
                "text/plain",
                "method=" + method + "; id=" + requestId
        );
        ReadEvent event = Allure.step("Ожидать JSON-RPC response", () -> pollResponse(timeout));
        if (event == null) {
            failed = true;
            throw new McpClientException("Timed out waiting for MCP response to request " + requestId);
        }
        if (event.error() != null) {
            failed = true;
            throw new McpClientException("Failed to read MCP STDOUT", event.error());
        }
        if (event.eof()) {
            failed = true;
            throw new McpClientException("MCP process closed STDOUT before sending a response");
        }

        JsonNode response;
        try {
            response = OBJECT_MAPPER.readTree(event.line());
        } catch (IOException exception) {
            failed = true;
            throw new McpClientException("MCP process returned invalid JSON: " + event.line(), exception);
        }

        if (response == null || !response.isObject()
                || !"2.0".equals(response.path("jsonrpc").asText())
                || !response.has("id")
                || !response.get("id").isIntegralNumber()
                || response.get("id").longValue() != requestId) {
            failed = true;
            throw new McpClientException("MCP response does not match request id " + requestId
                    + ": " + response);
        }

        Allure.addAttachment(
                "JSON-RPC response summary",
                "text/plain",
                summarizeResponse(response)
        );
        return response;
    }

    /**
     * Отправляет JSON-RPC notification без ожидания ответа.
     *
     * @param method имя notification
     * @param params объект параметров; {@code null} означает отсутствие params
     */
    public synchronized void notification(String method, ObjectNode params) {
        ensureUsable();
        validateMethod(method);

        ObjectNode notification = OBJECT_MAPPER.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        if (params != null) {
            notification.set("params", params);
        }
        Allure.step("Отправить JSON-RPC notification: " + method, () -> writeMessage(notification));
    }

    /**
     * Выполняет классический MCP handshake и переводит сессию в READY.
     *
     * @param protocolVersion запрашиваемая версия протокола
     * @param clientName имя AQA-клиента
     * @param clientVersion версия AQA-клиента
     * @param timeout таймаут ответа initialize
     * @return response на initialize
     */
    public synchronized JsonNode initialize(String protocolVersion,
                                             String clientName,
                                             String clientVersion,
                                             Duration timeout) {
        requireText(protocolVersion, "protocolVersion");
        requireText(clientName, "clientName");
        requireText(clientVersion, "clientVersion");

        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("protocolVersion", protocolVersion);
        params.putObject("capabilities");
        params.putObject("clientInfo")
                .put("name", clientName)
                .put("version", clientVersion);

        JsonNode response = request("initialize", params, timeout);
        if (response.has("error")) {
            throw new McpClientException("MCP initialize failed: " + response.get("error"));
        }
        if (!response.path("result").isObject()
                || !response.path("result").path("protocolVersion").isTextual()
                || !response.path("result").path("capabilities").isObject()
                || !response.path("result").path("serverInfo").isObject()) {
            throw new McpClientException("MCP initialize response does not match the expected contract");
        }

        notification("notifications/initialized", null);
        return response;
    }

    /**
     * Выполняет stateless MCP request, добавляя обязательную protocol metadata.
     *
     * @param method имя MCP-метода
     * @param params параметры метода без поля {@code _meta}; null означает пустой объект
     * @param protocolVersion stateless protocol version
     * @param clientCapabilities capabilities клиента
     * @param clientInfo необязательная информация о клиенте
     * @param timeout таймаут ответа
     * @return полный JSON-RPC response
     */
    public JsonNode requestStateless(String method,
                                     ObjectNode params,
                                     String protocolVersion,
                                     ObjectNode clientCapabilities,
                                     ObjectNode clientInfo,
                                     Duration timeout) {
        requireText(protocolVersion, "protocolVersion");
        Objects.requireNonNull(clientCapabilities, "clientCapabilities");

        ObjectNode statelessParams = params == null
                ? OBJECT_MAPPER.createObjectNode()
                : params.deepCopy();
        if (statelessParams.has("_meta")) {
            throw new IllegalArgumentException("params must not already contain _meta");
        }

        ObjectNode metadata = statelessParams.putObject("_meta");
        metadata.put("io.modelcontextprotocol/protocolVersion", protocolVersion);
        metadata.set("io.modelcontextprotocol/clientCapabilities", clientCapabilities.deepCopy());
        if (clientInfo != null) {
            metadata.set("io.modelcontextprotocol/clientInfo", clientInfo.deepCopy());
        }

        return request(method, statelessParams, timeout);
    }

    /**
     * Выполняет stateless discovery сервера.
     *
     * @param protocolVersion stateless protocol version
     * @param timeout таймаут ответа
     * @return discovery response
     */
    public JsonNode discoverStateless(String protocolVersion, Duration timeout) {
        ObjectNode clientInfo = OBJECT_MAPPER.createObjectNode()
                .put("name", McpTestData.CLIENT_NAME)
                .put("version", McpTestData.CLIENT_VERSION);
        return requestStateless(
                "server/discover",
                null,
                protocolVersion,
                OBJECT_MAPPER.createObjectNode(),
                clientInfo,
                timeout
        );
    }

    /**
     * Получает список инструментов через stateless MCP API.
     *
     * @param protocolVersion stateless protocol version
     * @param timeout таймаут ответа
     * @return tools/list response
     */
    public JsonNode listToolsStateless(String protocolVersion, Duration timeout) {
        return requestStateless(
                "tools/list",
                null,
                protocolVersion,
                OBJECT_MAPPER.createObjectNode(),
                null,
                timeout
        );
    }

    // Package-private raw transport hooks are used only by STDIO contract tests.
    synchronized void sendRawLine(String line) {
        ensureUsable();
        Objects.requireNonNull(line, "line");
        Allure.step("Отправить raw STDIO line", () -> {
            try {
                writer.write(line);
                writer.newLine();
                writer.flush();
            } catch (IOException exception) {
                failed = true;
                throw new McpClientException("Failed to write raw MCP line to STDIN", exception);
            }
        });
    }

    synchronized JsonNode awaitRawResponse(Duration timeout) {
        ensureUsable();
        validateTimeout(timeout);
        ReadEvent event = Allure.step("Ожидать raw STDIO response", () -> pollResponse(timeout));
        if (event == null) {
            failed = true;
            throw new McpClientException("Timed out waiting for raw MCP response");
        }
        if (event.error() != null) {
            failed = true;
            throw new McpClientException("Failed to read MCP STDOUT", event.error());
        }
        if (event.eof()) {
            failed = true;
            throw new McpClientException("MCP process closed STDOUT before sending a response");
        }
        try {
            return OBJECT_MAPPER.readTree(event.line());
        } catch (IOException exception) {
            failed = true;
            throw new McpClientException("MCP process returned invalid JSON: " + event.line(), exception);
        }
    }


    private ReadEvent pollResponse(Duration timeout) {
        try {
            return responses.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failed = true;
            throw new McpClientException("Interrupted while waiting for MCP response", exception);
        }
    }

    private static String summarizeResponse(JsonNode response) {
        StringBuilder summary = new StringBuilder()
                .append("jsonrpc=").append(response.path("jsonrpc").asText())
                .append("; id=").append(response.path("id").asText())
                .append("; hasError=").append(response.has("error"));

        if (response.has("error")) {
            summary.append("; errorCode=").append(response.path("error").path("code").asInt());
        }

        JsonNode result = response.path("result");
        if (result.isObject()) {
            summary.append("; resultFields=");
            List<String> fields = new java.util.ArrayList<>();
            result.fieldNames().forEachRemaining(fields::add);
            summary.append(String.join(",", fields));
            if (result.has("resultType")) {
                summary.append("; resultType=").append(result.path("resultType").asText());
            }
            if (result.has("isError")) {
                summary.append("; isError=").append(result.path("isError").asBoolean());
            }
            if (result.path("content").isArray()) {
                summary.append("; contentItems=").append(result.path("content").size());
            }
            if (result.path("tools").isArray()) {
                summary.append("; tools=").append(result.path("tools").size());
            }
        }
        return summary.toString();
    }

    private void writeMessage(ObjectNode message) {
        try {
            writer.write(OBJECT_MAPPER.writeValueAsString(message));
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            failed = true;
            throw new McpClientException("Failed to write MCP message to STDIN", exception);
        }
    }

    private void ensureUsable() {
        if (closed) {
            throw new IllegalStateException("MCP client is closed");
        }
        if (failed) {
            throw new McpClientException("MCP client cannot continue after a transport failure");
        }
    }

    private static void validateMethod(String method) {
        requireText(method, "method");
    }

    private static void validateTimeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            writer.close();
        } catch (IOException ignored) {
            // Closing the client is best-effort; process cleanup is owned by ManagedProcess.
        }
    }

    private record ReadEvent(String line, IOException error, boolean eof) {
        private static ReadEvent line(String value) {
            return new ReadEvent(value, null, false);
        }

        private static ReadEvent failure(Exception exception) {
            IOException ioException = exception instanceof IOException io
                    ? io : new IOException(exception);
            return new ReadEvent(null, ioException, false);
        }

        private static ReadEvent endOfStream() {
            return new ReadEvent(null, null, true);
        }
    }
}
