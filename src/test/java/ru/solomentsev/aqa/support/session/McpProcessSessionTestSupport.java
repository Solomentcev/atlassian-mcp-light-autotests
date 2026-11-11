package ru.solomentsev.aqa.support.session;

import io.qameta.allure.Step;
import ru.solomentsev.aqa.mcp.McpProcessSession;
import ru.solomentsev.aqa.process.McpServerLauncher;
import ru.solomentsev.aqa.process.ProcessBuilderLauncher;
import ru.solomentsev.aqa.support.OperatingSystem;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Подготовка fake MCP-процессов для тестов жизненного цикла сессии. */
public final class McpProcessSessionTestSupport {

    private McpProcessSessionTestSupport() {
    }

    @Step("Запустить classic MCP-сессию: protocolVersion={protocolVersion}")
    public static McpProcessSession startSession(McpServerLauncher launcher,
                                                 Map<String, String> environment,
                                                 Path workingDirectory,
                                                 String protocolVersion,
                                                 String clientName,
                                                 String clientVersion,
                                                 Duration timeout) throws Exception {
        return McpProcessSession.start(launcher, environment, workingDirectory,
                protocolVersion, clientName, clientVersion, timeout);
    }

    @Step("Запустить stateless MCP-сессию без classic initialize")
    public static McpProcessSession startStatelessSession(McpServerLauncher launcher,
                                                           Map<String, String> environment,
                                                           Path workingDirectory) throws Exception {
        return McpProcessSession.startWithoutHandshake(launcher, environment, workingDirectory);
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
