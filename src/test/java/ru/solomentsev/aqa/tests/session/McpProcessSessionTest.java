package ru.solomentsev.aqa.tests.session;

import ru.solomentsev.aqa.mcp.McpProcessSession;
import ru.solomentsev.aqa.support.fake.FakeMcpServer;
import ru.solomentsev.aqa.support.fake.FakeStatelessMcpServer;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import ru.solomentsev.aqa.process.ManagedProcess;
import ru.solomentsev.aqa.process.McpServerLauncher;
import ru.solomentsev.aqa.process.ProcessBuilderLauncher;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("AQA Test Infrastructure")
    @Tag("unit")
    @Tag("process")
class McpProcessSessionTest {

    @Test
    @Feature("Process Session Lifecycle")
    @Story("Запуск готовой MCP-сессии и завершение процесса при закрытии")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Запуск готовой MCP-сессии и завершение процесса при закрытии")
    @Description("Предусловия: Доступен fake MCP server с classic initialize handshake.\nШаги: запустить McpProcessSession; проверить доступность процесса и клиента; закрыть сессию.\nОжидаемый результат: readiness handshake завершён; процесс завершён с exit code 0.")
    @Tag("process")
    void shouldStartReadySessionAndStopProcessOnClose() throws Exception {
        Allure.step("Запуск готовой MCP-сессии и завершение процесса при закрытии", () -> {
            McpProcessSession session = McpProcessSession.start(
                    launcherFor(FakeMcpServer.class),
                    Map.of(),
                    null,
                    "2025-11-25",
                    "atlassian-mcp-light-autotests",
                    "1.0",
                    Duration.ofSeconds(5)
            );
            ManagedProcess process = session.process();

            try {
                assertThat(process.isAlive()).isTrue();
                assertThat(session.client()).isNotNull();
            } finally {
                session.close();
            }

            assertThat(process.waitFor(Duration.ofSeconds(3))).isTrue();
            assertThat(process.exitCode()).isZero();
        
        });
    }

    @Test
    @Feature("Process Session Lifecycle")
    @Story("Запуск stateless-сессии без classic initialize")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Запуск stateless-сессии без classic initialize")
    @Description("Предусловия: Доступен fake stateless MCP server.\nШаги: запустить сессию без handshake; выполнить server/discover и tools/list; закрыть сессию.\nОжидаемый результат: stateless запросы выполняются без classic initialize; процесс завершается с exit code 0.")
    @Tag("process")
    void shouldStartStatelessSessionWithoutClassicInitialize() throws Exception {
        Allure.step("Запуск stateless-сессии без classic initialize", () -> {
            McpProcessSession session = McpProcessSession.startWithoutHandshake(
                    launcherFor(FakeStatelessMcpServer.class),
                    Map.of(),
                    null
            );
            ManagedProcess process = session.process();

            try {
                assertThat(process.isAlive()).isTrue();

                var discovery = session.client().discoverStateless(
                        "2026-07-28",
                        Duration.ofSeconds(3)
                );
                assertThat(discovery.path("result").path("_meta")
                        .path("io.modelcontextprotocol/serverInfo")
                        .path("name").asText()).isEqualTo("fake-stateless-server");

                var tools = session.client().listToolsStateless(
                        "2026-07-28",
                        Duration.ofSeconds(3)
                );
                assertThat(tools.path("result").path("tools").isArray()).isTrue();
            } finally {
                session.close();
            }

            assertThat(process.waitFor(Duration.ofSeconds(3))).isTrue();
            assertThat(process.exitCode()).isZero();
        
        });
    }

    private static McpServerLauncher launcherFor(Class<?> serverClass) {
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java"
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

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}