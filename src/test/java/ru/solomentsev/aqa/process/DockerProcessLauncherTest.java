package ru.solomentsev.aqa.process;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerProcessLauncherTest {

    @Test
    void shouldBuildInteractiveStdioRunCommandWithoutTtyOrPublishedPorts() {
        List<String> command = DockerProcessLauncher.buildRunCommand(
                "docker",
                "aml-build_123",
                Path.of(".runtime.env"),
                "atlassian-mcp-light:latest",
                List.of("java", "-jar", "app.jar")
        );

        assertThat(command).containsSubsequence(
                "docker", "run", "--rm", "--name", "aml-build_123",
                "--label", "aqa.run-id=build_123", "-i", "--env-file"
        );
        assertThat(command).contains("atlassian-mcp-light:latest");
        assertThat(command).containsSubsequence("java", "-jar", "app.jar");
        assertThat(command).doesNotContain("-t", "-p", "--publish");
    }

    @Test
    void shouldRejectUnsafeRunId() {
        assertThatThrownBy(() -> new DockerProcessLauncher(
                "docker",
                "atlassian-mcp-light:latest",
                Path.of(".runtime.env"),
                "unsafe/run-id",
                5
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
    }

    @Test
    void shouldRejectZeroStopTimeout() {
        assertThatThrownBy(() -> new DockerProcessLauncher(
                "docker",
                "atlassian-mcp-light:latest",
                Path.of(".runtime.env"),
                "run-123",
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stopTimeoutSeconds");
    }
}
