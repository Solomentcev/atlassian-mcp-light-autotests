package ru.solomentsev.aqa.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Запускает системную команду средствами {@link ProcessBuilder}.
 *
 * <p>Команда передаётся списком аргументов без промежуточной shell-интерпретации.
 * STDOUT и STDERR остаются раздельными. Класс не знает о MCP и Docker lifecycle.</p>
 */
public final class ProcessBuilderLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessBuilderLauncher.class);

    /**
     * Запускает процесс с указанной командой.
     *
     * @param command executable и аргументы процесса
     * @param environment дополнительные или переопределяемые переменные окружения
     * @param workingDirectory рабочий каталог; {@code null} означает каталог JVM
     * @return управление запущенным процессом
     * @throws IOException если процесс не удалось запустить
     */
    public ManagedProcess launch(List<String> command,
                                 Map<String, String> environment,
                                 Path workingDirectory) throws IOException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(environment, "environment");

        if (command.isEmpty() || command.get(0) == null || command.get(0).isBlank()) {
            throw new IllegalArgumentException("command must contain an executable");
        }
        if (command.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("command must not contain null arguments");
        }

        return launch(command, environment, workingDirectory, null);
    }

    ManagedProcess launch(List<String> command,
                          Map<String, String> environment,
                          Path workingDirectory,
                          ProcessCleanup cleanup) throws IOException {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(environment, "environment");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(false);
        processBuilder.environment().putAll(environment);

        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        LOG.debug("Запуск процесса: executable={}, argumentsCount={}, environmentKeys={}, workingDirectory={}",
                command.get(0), command.size() - 1, environment.keySet(), workingDirectory);
        Process process = processBuilder.start();
        LOG.info("Процесс запущен: executable={}, pid={}", command.get(0), process.pid());
        return new ManagedProcess(process, cleanup);
    }
}