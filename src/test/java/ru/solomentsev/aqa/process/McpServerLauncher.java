package ru.solomentsev.aqa.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Запускает отдельный тестовый экземпляр MCP-сервера в выбранной среде. */
@FunctionalInterface
public interface McpServerLauncher {

    /**
     * Запускает тестовый экземпляр сервера.
     *
     * @param environment дополнительные переменные окружения процесса запуска
     * @param workingDirectory рабочий каталог процесса запуска или {@code null}
     * @return управление процессом и его STDIO-потоками
     * @throws IOException если процесс не удалось запустить
     */
    ManagedProcess launch(Map<String, String> environment,
                          Path workingDirectory) throws IOException;
}