package ru.solomentsev.aqa.process;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DockerMcpServerLauncherTest {

    @Test
    void shouldBuildInteractiveStdioRunCommandWithoutTtyOrPublishedPorts() {
        List<String> command = DockerMcpServerLauncher.buildRunCommand(
                "docker",
                "aml-build_123",
                Path.of(".runtime.env"),
                "atlassian-mcp-light:latest"
        );

        assertThat(command).containsSubsequence(
                "docker", "run", "--rm", "--name", "aml-build_123",
                "--label", "aqa.run-id=build_123", "-i", "--env-file"
        );
        assertThat(command).contains("atlassian-mcp-light:latest");
        assertThat(command).doesNotContain("-t", "-p", "--publish");
    }

    @Test
    void shouldRejectUnsafeRunId() {
        assertThatThrownBy(() -> new DockerMcpServerLauncher(
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
        assertThatThrownBy(() -> new DockerMcpServerLauncher(
                "docker",
                "atlassian-mcp-light:latest",
                Path.of(".runtime.env"),
                "run-123",
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stopTimeoutSeconds");
    }
}