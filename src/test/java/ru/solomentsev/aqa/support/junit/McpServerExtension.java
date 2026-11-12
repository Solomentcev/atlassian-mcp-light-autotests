package ru.solomentsev.aqa.support.junit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestWatcher;
import ru.solomentsev.aqa.config.McpServerConfiguration;
import ru.solomentsev.aqa.mcp.McpClient;
import ru.solomentsev.aqa.support.diagnostics.AllureDiagnostics;
import ru.solomentsev.aqa.mcp.McpProcessSession;

/**
 * Создаёт одну отдельную MCP-сессию на тестовый класс и закрывает её после класса.
 *
 * <p>Расширение предоставляет {@link McpProcessSession} и {@link McpClient}
 * через parameter injection в тестовые методы и lifecycle-методы JUnit.</p>
 */
public final class McpServerExtension
        implements BeforeAllCallback, AfterAllCallback, ParameterResolver, TestWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(McpServerExtension.class);
    private static final String SESSION_KEY = "mcp-process-session";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        String testClass = context.getRequiredTestClass().getSimpleName();
        LOG.info("Начало тестового класса: {}", testClass);
        McpServerTest testConfiguration = context.getRequiredTestClass()
                .getAnnotation(McpServerTest.class);
        McpServerConfiguration configuration = McpServerConfiguration.fromEnvironment();
        McpProcessSession session;
        if (testConfiguration.stateless()) {
            session = configuration.startStatelessSession();
        } else {
            session = configuration.startSession();
        }
        context.getStore(NAMESPACE).put(SESSION_KEY, session);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        McpProcessSession session = context.getStore(NAMESPACE)
                .remove(SESSION_KEY, McpProcessSession.class);
        if (session != null) {
            try {
                session.close();
                LOG.info("Завершение тестового класса: {}", context.getRequiredTestClass().getSimpleName());
            } catch (java.io.IOException exception) {
                throw new java.io.IOException("Failed to close MCP session after "
                        + context.getRequiredTestClass().getName(), exception);
            }
        }
    }


    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        LOG.error("Тест завершился ошибкой: {} ({})",
                context.getDisplayName(), cause.getClass().getSimpleName());
        McpProcessSession session = findSession(context);
        if (session != null) {
            AllureDiagnostics.attachStderr(session.stderr());
        }
    }

    private McpProcessSession findSession(ExtensionContext context) {
        ExtensionContext current = context;
        while (current != null) {
            McpProcessSession session = current.getStore(NAMESPACE)
                    .get(SESSION_KEY, McpProcessSession.class);
            if (session != null) {
                return session;
            }
            current = current.getParent().orElse(null);
        }
        return null;
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
        McpProcessSession session = findSession(extensionContext);
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