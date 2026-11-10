package ru.solomentsev.aqa.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Запускает тестовый экземпляр приложения и возвращает управление его потоками.
 *
 * <p>Интерфейс не зависит от способа запуска. Локальный Java-процесс и Docker CLI
 * могут предоставлять MCP Client одинаковый объект {@link ManagedProcess}.</p>
 */
public interface ProcessLauncher {

    /**
     * Запускает процесс с указанной командой.
     *
     * @param command исполняемый файл и аргументы отдельными элементами
     * @param environment дополнительные или переопределяемые переменные окружения
     * @param workingDirectory рабочий каталог; {@code null} означает каталог JVM
     * @return управление запущенным процессом
     * @throws IOException если процесс не удалось запустить
     */
    ManagedProcess launch(List<String> command,
                          Map<String, String> environment,
                          Path workingDirectory) throws IOException;
}
