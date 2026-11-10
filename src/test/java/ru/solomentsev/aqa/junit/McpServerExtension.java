package ru.solomentsev.aqa.junit;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import ru.solomentsev.aqa.mcp.McpClient;
import ru.solomentsev.aqa.mcp.McpProcessSession;

import java.io.IOException;

/**
 * Создаёт одну отдельную MCP-сессию на тестовый класс и закрывает её после класса.
 *
 * <p>Расширение предоставляет {@link McpProcessSession} и {@link McpClient}
 * через parameter injection в тестовые методы и конструкторы.</p>
 */
public final class McpServerExtension
        implements BeforeAllCallback, AfterAllCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(McpServerExtension.class);
    private static final String SESSION_KEY = "mcp-process-session";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        McpProcessSession session = McpServerConfiguration.fromEnvironment().startSession();
        context.getStore(NAMESPACE).put(SESSION_KEY, session);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        McpProcessSession session = context.getStore(NAMESPACE)
                .remove(SESSION_KEY, McpProcessSession.class);
        if (session != null) {
            try {
                session.close();
            } catch (IOException exception) {
                throw new IOException("Failed to close MCP session after "
                        + context.getRequiredTestClass().getName(), exception);
            }
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
                                     ExtensionContext extensionContext)
            throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        return type == McpClient.class || type == McpProcessSession.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
                                   ExtensionContext extensionContext)
            throws ParameterResolutionException {
        McpProcessSession session = extensionContext.getStore(NAMESPACE)
                .get(SESSION_KEY, McpProcessSession.class);
        if (session == null) {
            throw new ParameterResolutionException(
                    "MCP session is not available; use @McpServerTest on the test class");
        }

        Class<?> type = parameterContext.getParameter().getType();
        if (type == McpClient.class) {
            return session.client();
        }
        if (type == McpProcessSession.class) {
            return session;
        }
        throw new ParameterResolutionException("Unsupported MCP parameter type: " + type.getName());
    }
}
