package ru.solomentsev.aqa.mcp;

import ru.solomentsev.aqa.process.ManagedProcess;
import ru.solomentsev.aqa.process.ProcessLauncher;
import ru.solomentsev.aqa.process.StderrCollector;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Управляет жизненным циклом одного тестового экземпляра MCP-сервера.
 *
 * <p>Создаёт процесс, запускает независимый сбор STDERR, создаёт MCP Client
 * и при необходимости подтверждает готовность классическим initialize handshake.
 * Stateless-сессия может быть создана без classic handshake. При закрытии
 * сначала закрывается MCP-соединение, затем завершается процесс.</p>
 */
public final class McpProcessSession implements AutoCloseable {

    private static final int DEFAULT_STDERR_LIMIT = 64 * 1024;
    private static final Duration STDERR_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    private final ManagedProcess process;
    private final StderrCollector stderrCollector;
    private final McpClient client;
    private boolean closed;

    private McpProcessSession(ManagedProcess process,
                              StderrCollector stderrCollector,
                              McpClient client) {
        this.process = process;
        this.stderrCollector = stderrCollector;
        this.client = client;
    }

    /**
     * Запускает тестовый процесс и выполняет MCP readiness handshake.
     *
     * @param launcher способ запуска процесса
     * @param command команда и аргументы
     * @param environment дополнительные переменные окружения
     * @param workingDirectory рабочий каталог или {@code null}
     * @param protocolVersion версия классического MCP lifecycle
     * @param clientName имя клиента
     * @param clientVersion версия клиента
     * @param startupTimeout таймаут ответа initialize
     * @return готовая MCP-сессия
     * @throws IOException если процесс не удалось запустить
     */
    public static McpProcessSession start(ProcessLauncher launcher,
                                          List<String> command,
                                          Map<String, String> environment,
                                          Path workingDirectory,
                                          String protocolVersion,
                                          String clientName,
                                          String clientVersion,
                                          Duration startupTimeout) throws IOException {
        return start(launcher, command, environment, workingDirectory, protocolVersion,
                clientName, clientVersion, startupTimeout, true);
    }

    /**
     * Запускает тестовый процесс без классического initialize handshake.
     *
     * <p>Используется для проверки stateless MCP lifecycle, который не должен
     * зависеть от состояния классической сессии.</p>
     *
     * @param launcher способ запуска процесса
     * @param command команда и аргументы
     * @param environment дополнительные переменные окружения
     * @param workingDirectory рабочий каталог или {@code null}
     * @return сессия с доступным MCP Client
     * @throws IOException если процесс не удалось запустить
     */
    public static McpProcessSession startWithoutHandshake(ProcessLauncher launcher,
                                                           List<String> command,
                                                           Map<String, String> environment,
                                                           Path workingDirectory) throws IOException {
        return start(launcher, command, environment, workingDirectory, null,
                null, null, null, false);
    }

    private static McpProcessSession start(ProcessLauncher launcher,
                                           List<String> command,
                                           Map<String, String> environment,
                                           Path workingDirectory,
                                           String protocolVersion,
                                           String clientName,
                                           String clientVersion,
                                           Duration startupTimeout,
                                           boolean initialize) throws IOException {
        Objects.requireNonNull(launcher, "launcher");
        if (initialize) {
            Objects.requireNonNull(startupTimeout, "startupTimeout");
        }

        ManagedProcess process = launcher.launch(command, environment, workingDirectory);
        StderrCollector stderrCollector = new StderrCollector(process.stderr(), DEFAULT_STDERR_LIMIT);
        McpClient client = new McpClient(process.stdout(), process.stdin());

        try {
            if (!process.isAlive()) {
                throw new McpClientException("MCP process terminated before readiness handshake");
            }
            if (initialize) {
                client.initialize(protocolVersion, clientName, clientVersion, startupTimeout);
            }
            if (!process.isAlive()) {
                throw new McpClientException("MCP process terminated during readiness handshake");
            }
            return new McpProcessSession(process, stderrCollector, client);
        } catch (RuntimeException | Error exception) {
            client.close();
            try {
                process.close();
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            try {
                stderrCollector.await(STDERR_SHUTDOWN_TIMEOUT);
                stderrCollector.close();
            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    /**
     * Возвращает MCP Client готовой сессии.
     *
     * @return MCP Client
     */
    public McpClient client() {
        ensureOpen();
        return client;
    }

    /**
     * Возвращает управление процессом для проверок состояния и диагностики.
     *
     * @return запущенный процесс
     */
    public ManagedProcess process() {
        return process;
    }

    /**
     * Возвращает накопленный хвост STDERR.
     *
     * @return диагностический текст
     */
    public String stderr() {
        return stderrCollector.content();
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("MCP process session is closed");
        }
    }

    /**
     * Закрывает MCP Client, завершает процесс и останавливает сбор STDERR.
     *
     * @throws IOException если не удалось закрыть потоки процесса
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        client.close();

        IOException closeException = null;
        try {
            process.close();
        } catch (IOException exception) {
            closeException = exception;
        }

        try {
            stderrCollector.await(STDERR_SHUTDOWN_TIMEOUT);
            stderrCollector.close();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            if (closeException == null) {
                closeException = exception;
            } else {
                closeException.addSuppressed(exception);
            }
        }

        if (closeException != null) {
            throw closeException;
        }
    }
}
