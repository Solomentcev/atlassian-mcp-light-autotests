package ru.solomentsev.aqa.process;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Управляемая обёртка над процессом, запущенным AQA. */
public final class ManagedProcess implements AutoCloseable {

    private static final Duration DEFAULT_GRACEFUL_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_FORCED_TIMEOUT = Duration.ofSeconds(3);

    private final Process process;
    private final ProcessCleanup cleanup;
    private boolean closed;

    ManagedProcess(Process process) {
        this(process, null);
    }

    ManagedProcess(Process process, ProcessCleanup cleanup) {
        this.process = Objects.requireNonNull(process, "process");
        this.cleanup = cleanup;
    }

    /** @return поток STDIN дочернего процесса */
    public OutputStream stdin() {
        return process.getOutputStream();
    }

    /** @return поток STDOUT дочернего процесса */
    public InputStream stdout() {
        return process.getInputStream();
    }

    /** @return поток STDERR дочернего процесса */
    public InputStream stderr() {
        return process.getErrorStream();
    }

    /** @return {@code true}, если процесс ещё выполняется */
    public boolean isAlive() {
        return process.isAlive();
    }

    /** @return PID процесса */
    public long pid() {
        return process.pid();
    }

    /**
     * Ожидает завершения процесса заданное время.
     *
     * @param timeout максимальное время ожидания
     * @return {@code true}, если процесс завершился за отведённое время
     * @throws InterruptedException если ожидание было прервано
     */
    public boolean waitFor(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** @return код завершения процесса */
    public int exitCode() {
        return process.exitValue();
    }

    /** Запрашивает штатное завершение процесса. */
    public void destroy() {
        process.destroy();
    }

    /** Принудительно завершает процесс. */
    public void destroyForcibly() {
        process.destroyForcibly();
    }

    /**
     * Закрывает STDIN, ожидает штатного завершения и при необходимости
     * применяет destroy и destroyForcibly. После принудительной остановки
     * обязательно проверяет, что процесс действительно завершился.
     *
     * @throws IOException если процесс не завершился или не удалось закрыть его потоки
     */
    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        IOException closeException = null;
        boolean cleanupCompleted = cleanup == null;
        boolean interrupted = false;

        try {
            process.getOutputStream().close();
        } catch (IOException exception) {
            closeException = append(closeException, exception);
        }

        try {
            if (!process.waitFor(DEFAULT_GRACEFUL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroy();
                if (!process.waitFor(DEFAULT_FORCED_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(DEFAULT_FORCED_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                        closeException = append(closeException, new IOException(
                                "Process did not terminate after destroyForcibly (pid=" + process.pid() + ")"));
                    }
                }
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            process.destroyForcibly();
            try {
                if (!process.waitFor(DEFAULT_FORCED_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    exception.addSuppressed(new IOException(
                            "Process did not terminate after destroyForcibly (pid=" + process.pid() + ")"));
                }
            } catch (InterruptedException forcedWaitException) {
                exception.addSuppressed(forcedWaitException);
            }
            closeException = append(closeException,
                    new IOException("Interrupted while waiting for process termination", exception));
        }

        try {
            process.getInputStream().close();
        } catch (IOException exception) {
            closeException = append(closeException, exception);
        }

        try {
            process.getErrorStream().close();
        } catch (IOException exception) {
            closeException = append(closeException, exception);
        }

        if (cleanup != null) {
            try {
                cleanup.cleanup();
                cleanupCompleted = true;
            } catch (IOException exception) {
                closeException = append(closeException, exception);
            }
        }

        closed = !process.isAlive() && cleanupCompleted;
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (closeException != null) {
            throw closeException;
        }
    }

    private static IOException append(IOException current, IOException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }
}