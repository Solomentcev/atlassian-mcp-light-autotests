package ru.solomentsev.aqa.tests.session;

import ru.solomentsev.aqa.mcp.McpClient;
import ru.solomentsev.aqa.mcp.McpProcessSession;
import ru.solomentsev.aqa.config.McpServerConfiguration;
import ru.solomentsev.aqa.support.fake.FakeMcpServer;
import ru.solomentsev.aqa.support.fake.FakeStatelessMcpServer;
import ru.solomentsev.aqa.testdata.McpTestData;
import com.fasterxml.jackson.databind.JsonNode;

import io.qameta.allure.Allure;
import io.qameta.allure.Step;
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

import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThat;

@Epic("AQA Test Infrastructure")
@Tag("unit")
@Tag("process")
@Tag("regression")
class McpProcessSessionTest {

    @Test
    @Feature("Process Session Lifecycle")
    @Story("Запуск готовой MCP-сессии и завершение процесса при закрытии")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Classic session [protocolVersion=" + McpTestData.CLASSIC_PROTOCOL_VERSION + ", client=" + McpTestData.CLIENT_NAME + "/" + McpTestData.CLIENT_VERSION + ", startupTimeout=" + McpTestData.DEFAULT_STARTUP_TIMEOUT_MS + " ms, shutdownTimeout=" + McpTestData.PROCESS_SHUTDOWN_TIMEOUT_MS + " ms]: handshake готов, close завершает процесс")
    @Description("Предусловия: доступен FakeMcpServer, который отвечает на initialize и notifications/initialized.\nШаги: запустить classic McpProcessSession с protocolVersion=" + McpTestData.CLASSIC_PROTOCOL_VERSION + " и startup timeout=" + McpTestData.DEFAULT_STARTUP_TIMEOUT_MS + " ms; убедиться, что процесс активен и MCP Client создан; закрыть сессию.\nОжидаемый результат: start() возвращает только после успешного readiness handshake; после close() процесс завершается в пределах " + McpTestData.PROCESS_SHUTDOWN_TIMEOUT_MS + " ms с exit code=0.")
    @Tag("process")
    void shouldStartReadySessionAndStopProcessOnClose() throws Exception {
        Allure.parameter("Session mode", McpTestData.CLASSIC_SESSION_MODE);
        Allure.parameter("MCP protocolVersion", McpTestData.CLASSIC_PROTOCOL_VERSION);
        Allure.parameter("Client name", McpTestData.CLIENT_NAME);
        Allure.parameter("Client version", McpTestData.CLIENT_VERSION);
        Allure.parameter("Startup timeout", McpTestData.DEFAULT_STARTUP_TIMEOUT.toMillis() + " ms");
        Allure.parameter("Shutdown timeout", McpTestData.PROCESS_SHUTDOWN_TIMEOUT.toMillis() + " ms");
        McpProcessSession session = startSession(
                launcherFor(FakeMcpServer.class),
                Map.of(),
                null,
                McpTestData.CLASSIC_PROTOCOL_VERSION,
                McpTestData.CLIENT_NAME,
                McpTestData.CLIENT_VERSION,
                McpTestData.DEFAULT_STARTUP_TIMEOUT
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
    @DisplayName("Stateless session [protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + ", без initialize, shutdownTimeout=" + McpTestData.PROCESS_SHUTDOWN_TIMEOUT_MS + " ms]: discover и tools/list успешны, close завершает процесс")
    @Description("Предусловия: доступен FakeStatelessMcpServer, поддерживающий server/discover и tools/list.\nШаги: запустить McpProcessSession без classic handshake; выполнить server/discover и tools/list с protocolVersion=" + McpTestData.STATELESS_PROTOCOL_VERSION + "; закрыть сессию.\nОжидаемый результат: оба stateless-запроса обрабатываются без initialize; serverInfo.name=fake-stateless-server; tools/list возвращает массив; после close() процесс завершается в пределах " + McpTestData.PROCESS_SHUTDOWN_TIMEOUT_MS + " ms с exit code=0.")
    @Tag("process")
    void shouldStartStatelessSessionWithoutClassicInitialize() throws Exception {
        Allure.parameter("Session mode", McpTestData.STATELESS_SESSION_MODE);
        Allure.parameter("MCP protocolVersion", McpTestData.STATELESS_PROTOCOL_VERSION);
        Allure.parameter("Classic initialize", "не выполняется");
        Allure.parameter("Shutdown timeout", McpTestData.PROCESS_SHUTDOWN_TIMEOUT.toMillis() + " ms");
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

    @Step("Выполнить server/discover: protocolVersion={version}, timeout={timeout}")
    private static JsonNode discoverStateless(McpClient client, String version, Duration timeout) {
        return client.discoverStateless(version, timeout);
    }

    @Step("Выполнить stateless tools/list: protocolVersion={version}, timeout={timeout}")
    private static JsonNode listToolsStateless(McpClient client, String version, Duration timeout) {
        return client.listToolsStateless(version, timeout);
    }

    @Step("Запустить classic MCP-сессию: protocolVersion={protocolVersion}")
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

    @Step("Запустить stateless MCP-сессию без classic initialize")
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