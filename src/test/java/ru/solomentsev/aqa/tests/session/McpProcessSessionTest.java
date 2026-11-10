package ru.solomentsev.aqa.tests.session;

import ru.solomentsev.aqa.mcp.McpClient;
import ru.solomentsev.aqa.mcp.McpProcessSession;
import ru.solomentsev.aqa.config.McpServerConfiguration;
import ru.solomentsev.aqa.support.fake.FakeMcpServer;
import ru.solomentsev.aqa.support.fake.FakeStatelessMcpServer;
import ru.solomentsev.aqa.testdata.McpTestData;
import com.fasterxml.jackson.databind.JsonNode;

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
    @DisplayName("Classic MCP session, protocolVersion=" + McpTestData.CLASSIC_PROTOCOL_VERSION + "; graceful shutdown")
    @Description("Предусловия: Доступен fake MCP server с classic initialize handshake.\nШаги: запустить McpProcessSession; проверить доступность процесса и клиента; закрыть сессию.\nОжидаемый результат: readiness handshake завершён; процесс завершён с exit code 0.")
    @Tag("process")
    void shouldStartReadySessionAndStopProcessOnClose() throws Exception {
        McpProcessSession session = startSession(
                launcherFor(FakeMcpServer.class),
                Map.of(),
                null,
                McpTestData.CLASSIC_PROTOCOL_VERSION,
                McpTestData.CLIENT_NAME,
                McpTestData.CLIENT_VERSION,
                McpServerConfiguration.DEFAULT_STARTUP_TIMEOUT
        );
        ManagedProcess process = session.process();

        try {
            assertThat(process.isAlive()).as("После запуска MCP-сессии процесс должен быть активен").isTrue();
            assertThat(session.client()).as("Запущенная MCP-сессия должна предоставить MCP Client").isNotNull();
        } finally {
            Allure.step("Закрыть MCP-сессию", session::close);
        }

        assertThat(Allure.step("Дождаться завершения MCP-процесса", () -> process.waitFor(McpTestData.PROCESS_SHUTDOWN_TIMEOUT)))
                .as("MCP-процесс должен завершиться после закрытия сессии")
                .isTrue();
        assertThat(process.exitCode()).as("Fake MCP server должен завершиться с кодом 0").isZero();
    
    
    }

    @Test
    @Feature("Process Session Lifecycle")
    @Story("Запуск stateless-сессии без classic initialize")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Stateless MCP session, protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + "; без initialize")
    @Description("Предусловия: Доступен fake stateless MCP server.\nШаги: запустить сессию без handshake; выполнить server/discover и tools/list; закрыть сессию.\nОжидаемый результат: stateless запросы выполняются без classic initialize; процесс завершается с exit code 0.")
    @Tag("process")
    void shouldStartStatelessSessionWithoutClassicInitialize() throws Exception {
        McpProcessSession session = startStatelessSession(
                launcherFor(FakeStatelessMcpServer.class),
                Map.of(),
                null
        );
        ManagedProcess process = session.process();

        try {
            assertThat(process.isAlive()).as("После запуска stateless-сессии процесс должен быть активен").isTrue();

            var discovery = discoverStateless(session.client(), 
                    McpTestData.STATELESS_PROTOCOL_VERSION,
                    McpTestData.PROCESS_SHUTDOWN_TIMEOUT
            );
            assertThat(discovery.path("result").path("_meta")
                    .path("io.modelcontextprotocol/serverInfo")
                    .path("name").asText())
                    .as("server/discover должен вернуть metadata fake-stateless-server")
                    .isEqualTo("fake-stateless-server");

            var tools = listToolsStateless(session.client(), 
                    McpTestData.STATELESS_PROTOCOL_VERSION,
                    McpTestData.PROCESS_SHUTDOWN_TIMEOUT
            );
            assertThat(tools.path("result").path("tools").isArray())
                    .as("tools/list должен вернуть массив инструментов")
                    .isTrue();
        } finally {
            Allure.step("Закрыть stateless MCP-сессию", session::close);
        }

        assertThat(Allure.step("Дождаться завершения stateless MCP-процесса",
                () -> process.waitFor(McpTestData.PROCESS_SHUTDOWN_TIMEOUT)))
                .as("Stateless MCP-процесс должен завершиться после закрытия сессии")
                .isTrue();
        assertThat(process.exitCode()).as("Fake stateless MCP server должен завершиться с кодом 0").isZero();
    
    
    }

    private static JsonNode discoverStateless(McpClient client, String version, Duration timeout) {
        return client.discoverStateless(version, timeout);
    }

    private static JsonNode listToolsStateless(McpClient client, String version, Duration timeout) {
        return client.listToolsStateless(version, timeout);
    }

    private static McpProcessSession startSession(McpServerLauncher launcher,
                                                   Map<String, String> environment,
                                                   Path workingDirectory,
                                                   String protocolVersion,
                                                   String clientName,
                                                   String clientVersion,
                                                   Duration timeout) throws Exception {
        return McpProcessSession.start(launcher, environment, workingDirectory,
                protocolVersion, clientName, clientVersion, timeout);
    }

    private static McpProcessSession startStatelessSession(McpServerLauncher launcher,
                                                           Map<String, String> environment,
                                                           Path workingDirectory) throws Exception {
        return McpProcessSession.startWithoutHandshake(launcher, environment, workingDirectory);
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