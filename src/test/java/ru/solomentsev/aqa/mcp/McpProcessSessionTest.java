package ru.solomentsev.aqa.mcp;

import org.junit.jupiter.api.Test;
import ru.solomentsev.aqa.process.DefaultProcessLauncher;
import ru.solomentsev.aqa.process.ManagedProcess;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpProcessSessionTest {

    @Test
    void shouldStartReadySessionAndStopProcessOnClose() throws Exception {
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java"
        ).toString();
        String testClasspath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path")
        );

        McpProcessSession session = McpProcessSession.start(
                new DefaultProcessLauncher(),
                List.of(
                        javaExecutable,
                        "-cp",
                        testClasspath,
                        FakeMcpServer.class.getName()
                ),
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
    }

    @Test
    void shouldStartStatelessSessionWithoutClassicInitialize() throws Exception {
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java"
        ).toString();
        String testClasspath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path")
        );

        McpProcessSession session = McpProcessSession.startWithoutHandshake(
                new DefaultProcessLauncher(),
                List.of(
                        javaExecutable,
                        "-cp",
                        testClasspath,
                        FakeStatelessMcpServer.class.getName()
                ),
                Map.of(),
                null
        );
        var process = session.process();

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
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
