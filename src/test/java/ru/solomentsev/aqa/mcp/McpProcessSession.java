package ru.solomentsev.aqa.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.solomentsev.aqa.process.ManagedProcess;
import ru.solomentsev.aqa.process.McpServerLauncher;
import ru.solomentsev.aqa.process.StderrCollector;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Управляет жизненным циклом одного тестового экземпляра MCP-сервера. */
public final class McpProcessSession implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(McpProcessSession.class);

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
     * Запускает тестовый экземпляр и выполняет MCP readiness handshake.
     *
     * @param launcher способ запуска тестового экземпляра
     * @param environment дополнительные переменные окружения
     * @param workingDirectory рабочий каталог или {@code null}
     * @param protocolVersion версия классического MCP lifecycle
     * @param clientName имя клиента
     * @param clientVersion версия клиента
     * @param startupTimeout таймаут ответа initialize
     * @return готовая MCP-сессия
     * @throws IOException если процесс не удалось запустить
     */
    public static McpProcessSession start(McpServerLauncher launcher,
                                          Map<String, String> environment,
                                          Path workingDirectory,
                                          String protocolVersion,
                                          String clientName,
                                          String clientVersion,
                                          Duration startupTimeout) throws IOException {
        return start(launcher, environment, workingDirectory, protocolVersion,
                clientName, clientVersion, startupTimeout, true);
    }

    /**
     * Запускает тестовый экземпляр без классического initialize handshake.
     *
     * @param launcher способ запуска тестового экземпляра
     * @param environment дополнительные переменные окружения
     * @param workingDirectory рабочий каталог или {@code null}
     * @return сессия с доступным MCP Client
     * @throws IOException если процесс не удалось запустить
     */
    public static McpProcessSession startWithoutHandshake(McpServerLauncher launcher,
                                                           Map<String, String> environment,
                                                           Path workingDirectory) throws IOException {
        return start(launcher, environment, workingDirectory, null,
                null, null, null, false);
    }

    private static McpProcessSession start(McpServerLauncher launcher,
                                           Map<String, String> environment,
                                           Path workingDirectory,
                                           String protocolVersion,
                                           String clientName,
                                           String clientVersion,
                                           Duration startupTimeout,
                                           boolean initialize) throws IOException {
        Objects.requireNonNull(launcher, "launcher");
        Objects.requireNonNull(environment, "environment");
        if (initialize) {
            Objects.requireNonNull(startupTimeout, "startupTimeout");
        }

        LOG.info("Запуск тестовой MCP-сессии: launcher={}, handshake={}",
                launcher.getClass().getSimpleName(), initialize);
        ManagedProcess process = launcher.launch(environment, workingDirectory);
        LOG.info("Тестовый MCP-процесс создан: pid={}", process.pid());
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
            LOG.info("MCP-сессия готова: pid={}, mode={}",
                    process.pid(), initialize ? "classic" : "stateless");
            return new McpProcessSession(process, stderrCollector, client);
        } catch (RuntimeException | Error exception) {
            LOG.error("Не удалось запустить MCP-сессию: pid={}", process.pid(), exception);
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

    /** @return MCP Client готовой сессии */
    public McpClient client() {
        ensureOpen();
        return client;
    }

    /** @return управление процессом для проверок состояния и диагностики */
    public ManagedProcess process() {
        return process;
    }

    /** @return накопленный хвост STDERR */
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
     * @throws IOException если процесс или его потоки не удалось корректно закрыть
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        LOG.info("Завершение MCP-сессии: pid={}", process.pid());
        client.close();

        IOException closeException = null;
        try {
            process.close();
        } catch (IOException exception) {
            closeException = exception;
        }

        boolean interrupted = false;
        try {
            stderrCollector.await(STDERR_SHUTDOWN_TIMEOUT);
        } catch (InterruptedException exception) {
            interrupted = true;
            IOException interruptedException = new IOException(
                    "Interrupted while waiting for STDERR collector", exception);
            if (closeException == null) {
                closeException = interruptedException;
            } else {
                closeException.addSuppressed(interruptedException);
            }
        }

        try {
            stderrCollector.close();
        } catch (IOException exception) {
            if (closeException == null) {
                closeException = exception;
            } else {
                closeException.addSuppressed(exception);
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        if (closeException != null) {
            LOG.error("Ошибка при завершении MCP-сессии: pid={}", process.pid(), closeException);
            throw closeException;
        }
        closed = true;
        LOG.info("MCP-сессия завершена: pid={}, stderrCharacters={}",
                process.pid(), stderrCollector.content().length());
    }
}