package ru.solomentsev.aqa.process;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultProcessLauncherTest {

    @Test
    void shouldLaunchProcessAndCaptureStderrSeparately() throws Exception {
        String executable = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java"
        ).toString();

        ProcessLauncher launcher = new DefaultProcessLauncher();
        ManagedProcess process = launcher.launch(
                List.of(executable, "-version"),
                Map.of(),
                null
        );
        StderrCollector stderrCollector = new StderrCollector(process.stderr(), 8_192);

        try {
            assertThat(process.waitFor(Duration.ofSeconds(15))).isTrue();
            assertThat(process.exitCode()).isZero();
            assertThat(stderrCollector.await(Duration.ofSeconds(3))).isTrue();
            assertThat(stderrCollector.content()).contains("version");
        } finally {
            process.close();
            stderrCollector.close();
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
