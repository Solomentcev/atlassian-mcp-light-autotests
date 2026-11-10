package ru.solomentsev.aqa.process;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Обёртка над процессом, запущенным AQA.
 *
 * <p>Предоставляет независимые потоки STDIN, STDOUT и STDERR, а также
 * операции ожидания и завершения процесса.</p>
 */
public final class ManagedProcess implements AutoCloseable {

    private static final Duration DEFAULT_GRACEFUL_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_FORCED_TIMEOUT = Duration.ofSeconds(3);

    private final Process process;

    ManagedProcess(Process process) {
        this.process = Objects.requireNonNull(process, "process");
    }

    /**
     * Возвращает поток STDIN дочернего процесса.
     *
     * @return поток для отправки данных процессу
     */
    public OutputStream stdin() {
        return process.getOutputStream();
    }

    /**
     * Возвращает поток STDOUT дочернего процесса.
     *
     * @return поток с данными процесса
     */
    public InputStream stdout() {
        return process.getInputStream();
    }

    /**
     * Возвращает поток STDERR дочернего процесса.
     *
     * @return поток диагностики процесса
     */
    public InputStream stderr() {
        return process.getErrorStream();
    }

    /**
     * Проверяет, работает ли процесс.
     *
     * @return {@code true}, если процесс ещё выполняется
     */
    public boolean isAlive() {
        return process.isAlive();
    }

    /**
     * Возвращает идентификатор процесса операционной системы.
     *
     * @return PID процесса
     */
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

    /**
     * Возвращает exit code завершившегося процесса.
     *
     * @return код завершения
     * @throws IllegalThreadStateException если процесс ещё выполняется
     */
    public int exitCode() {
        return process.exitValue();
    }

    /**
     * Запрашивает штатное завершение процесса.
     */
    public void destroy() {
        process.destroy();
    }

    /**
     * Принудительно завершает процесс.
     */
    public void destroyForcibly() {
        process.destroyForcibly();
    }

    /**
     * Закрывает STDIN, ожидает штатного завершения и при необходимости
     * последовательно применяет destroy и destroyForcibly.
     *
     * @throws IOException если не удалось закрыть потоки процесса
     */
    @Override
    public void close() throws IOException {
        IOException closeException = null;
        try {
            process.getOutputStream().close();
        } catch (IOException exception) {
            closeException = exception;
        }

        try {
            if (!waitFor(DEFAULT_GRACEFUL_TIMEOUT)) {
                destroy();
                if (!waitFor(DEFAULT_FORCED_TIMEOUT)) {
                    destroyForcibly();
                    waitFor(DEFAULT_FORCED_TIMEOUT);
                }
            }
        } catch (InterruptedException exception) {
            destroyForcibly();
            Thread.currentThread().interrupt();
        }

        try {
            process.getInputStream().close();
        } catch (IOException exception) {
            if (closeException == null) {
                closeException = exception;
            } else {
                closeException.addSuppressed(exception);
            }
        }

        try {
            process.getErrorStream().close();
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
