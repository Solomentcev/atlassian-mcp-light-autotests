package ru.solomentsev.aqa.process;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Независимо читает STDERR процесса и хранит ограниченный хвост диагностики.
 *
 * <p>Ограничение размера предотвращает неограниченное потребление памяти,
 * если тестируемое приложение пишет много диагностических сообщений.</p>
 */
public final class StderrCollector implements AutoCloseable {

    private final InputStream inputStream;
    private final int maxCharacters;
    private final StringBuilder buffer = new StringBuilder();
    private final Thread readerThread;
    private volatile IOException readException;

    /**
     * Создаёт collector и сразу запускает фоновое чтение потока.
     *
     * @param inputStream STDERR тестируемого процесса
     * @param maxCharacters максимальный размер сохраняемого хвоста; должен быть положительным
     */
    public StderrCollector(InputStream inputStream, int maxCharacters) {
        this.inputStream = Objects.requireNonNull(inputStream, "inputStream");
        if (maxCharacters < 1) {
            throw new IllegalArgumentException("maxCharacters must be positive");
        }
        this.maxCharacters = maxCharacters;
        this.readerThread = new Thread(this::readStream, "aqa-stderr-collector");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    private void readStream() {
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            char[] chunk = new char[1024];
            int read;
            while ((read = reader.read(chunk)) != -1) {
                append(chunk, read);
            }
        } catch (IOException exception) {
            readException = exception;
        }
    }

    private void append(char[] chunk, int length) {
        synchronized (buffer) {
            buffer.append(chunk, 0, length);
            int overflow = buffer.length() - maxCharacters;
            if (overflow > 0) {
                buffer.delete(0, overflow);
            }
        }
    }

    /**
     * Возвращает сохранённый хвост STDERR.
     *
     * @return диагностический текст
     */
    public String content() {
        synchronized (buffer) {
            return buffer.toString();
        }
    }

    /**
     * Возвращает ошибку чтения, если она возникла.
     *
     * @return ошибка чтения или {@code null}
     */
    public IOException readException() {
        return readException;
    }

    /**
     * Ожидает завершения чтения потока.
     *
     * @param timeout максимальное время ожидания
     * @return {@code true}, если поток чтения завершился
     * @throws InterruptedException если ожидание было прервано
     */
    public boolean await(Duration timeout) throws InterruptedException {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        readerThread.join(Math.max(1, timeout.toMillis()));
        return !readerThread.isAlive();
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
        try {
            readerThread.join(TimeUnit.SECONDS.toMillis(2));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
