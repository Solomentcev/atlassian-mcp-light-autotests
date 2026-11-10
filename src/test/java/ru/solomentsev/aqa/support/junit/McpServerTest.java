package ru.solomentsev.aqa.support.junit;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Подключает отдельную MCP-сессию на тестовый класс.
 *
 * <p>Методы класса выполняются последовательно и используют один процесс.
 * Для тестов, которым требуется отдельное начальное состояние сервера,
 * следует выделять отдельный тестовый класс.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(McpServerExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
public @interface McpServerTest {
}
