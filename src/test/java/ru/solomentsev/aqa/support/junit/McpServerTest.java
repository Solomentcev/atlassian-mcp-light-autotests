package ru.solomentsev.aqa.support.junit;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Подключает отдельную MCP-сессию на тестовый класс.
 *
 * <p>Каждый класс получает собственный MCP-процесс и сессию.
 * Параллельный запуск классов и последовательный запуск методов
 * настраиваются централизованно через JUnit Platform.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(McpServerExtension.class)
public @interface McpServerTest {

    /**
     * Запустить процесс без classic initialize handshake для stateless-сценариев.
     *
     * @return {@code true}, если MCP-сессия должна начинаться без initialize
     */
    boolean stateless() default false;
}
