package ru.solomentsev.aqa.process;

import java.io.IOException;

/**
 * Дополнительная очистка ресурсов, созданных вместе с дочерним процессом.
 */
@FunctionalInterface
interface ProcessCleanup {

    /**
     * Освобождает связанные с процессом внешние ресурсы.
     *
     * @throws IOException если очистка не удалась
     */
    void cleanup() throws IOException;
}
