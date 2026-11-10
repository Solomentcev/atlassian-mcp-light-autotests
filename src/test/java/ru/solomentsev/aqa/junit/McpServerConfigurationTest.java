package ru.solomentsev.aqa.junit;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerConfigurationTest {

    @Test
    void shouldReadLocalModeFromEnvironment() {
        McpServerConfiguration configuration = McpServerConfiguration.from(
                Map.of(
                        "AQA_SERVER_MODE", "local",
                        "AQA_SERVER_JAR", "server/target/server.jar"
                ),
                new Properties()
        );

        assertThat(configuration.mode()).isEqualTo("local");
    }

    @Test
    void shouldPreferSystemPropertiesToEnvironment() {
        Properties properties = new Properties();
        properties.setProperty("aqa.server.mode", "docker");
        properties.setProperty("aqa.docker.image", "atlassian-mcp-light:test");
        properties.setProperty("aqa.docker.envFile", ".runtime.env");

        McpServerConfiguration configuration = McpServerConfiguration.from(
                Map.of(
                        "AQA_SERVER_MODE", "local",
                        "AQA_SERVER_JAR", "server.jar"
                ),
                properties
        );

        assertThat(configuration.mode()).isEqualTo("docker");
    }

    @Test
    void shouldRequireJarInLocalMode() {
        assertThatThrownBy(() -> McpServerConfiguration.from(
                Map.of("AQA_SERVER_MODE", "local"),
                new Properties()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AQA_SERVER_JAR");
    }

    @Test
    void shouldRequireImageAndEnvFileInDockerMode() {
        assertThatThrownBy(() -> McpServerConfiguration.from(
                Map.of(
                        "AQA_SERVER_MODE", "docker",
                        "AQA_DOCKER_IMAGE", "atlassian-mcp-light:test"
                ),
                new Properties()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AQA_DOCKER_ENV_FILE");
    }

    @Test
    void shouldRejectUnknownMode() {
        assertThatThrownBy(() -> McpServerConfiguration.from(
                Map.of("AQA_SERVER_MODE", "remote"),
                new Properties()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AQA_SERVER_MODE");
    }
}
