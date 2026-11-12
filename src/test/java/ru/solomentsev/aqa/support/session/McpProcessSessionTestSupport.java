package ru.solomentsev.aqa.support.session;

import io.qameta.allure.Allure;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import ru.solomentsev.aqa.config.McpServerConfiguration;
import ru.solomentsev.aqa.mcp.McpProcessSession;
import ru.solomentsev.aqa.process.McpServerLauncher;
import ru.solomentsev.aqa.process.ProcessBuilderLauncher;
import ru.solomentsev.aqa.support.OperatingSystem;

/** Подготовка fake MCP-процессов для тестов жизненного цикла сессии. */
public final class McpProcessSessionTestSupport {

    private McpProcessSessionTestSupport() {
    }

    public static McpProcessSession startSession(McpServerLauncher launcher,
                                                 Map<String, String> environment,
                                                 Path workingDirectory,
                                                 String protocolVersion,
                                                 String clientName,
                                                 String clientVersion,
                                                 Duration timeout) throws Exception {
        return Allure.step("Запустить classic MCP-сессию: protocolVersion=" + protocolVersion,
                () -> McpProcessSession.start(launcher, environment, workingDirectory,
                        protocolVersion, clientName, clientVersion, timeout));
    }

    public static McpProcessSession startStatelessSession(McpServerLauncher launcher,
                                                           Map<String, String> environment,
                                                           Path workingDirectory) throws Exception {
        return Allure.step("Запустить stateless MCP-сессию без classic initialize",
                () -> McpProcessSession.startWithoutHandshake(launcher, environment, workingDirectory));
    }

    public static McpProcessSession startConfiguredStatelessSession() throws Exception {
        return Allure.step("Запустить настроенный MCP-сервер без classic initialize",
                () -> McpServerConfiguration.fromEnvironment().startStatelessSession());
    }

    public static McpServerLauncher launcherFor(Class<?> serverClass) {
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                OperatingSystem.isWindows() ? "java.exe" : "java"
        ).toString();
        String testClasspath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path")
        );
        List<String> command = List.of(
                javaExecutable,
                "-cp",
                testClasspath,
                serverClass.getName()
        );
        return (environment, workingDirectory) ->
                new ProcessBuilderLauncher().launch(command, environment, workingDirectory);
    }
}
