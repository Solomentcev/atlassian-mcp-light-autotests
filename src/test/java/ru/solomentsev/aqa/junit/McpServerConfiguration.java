package ru.solomentsev.aqa.junit;

import ru.solomentsev.aqa.mcp.McpProcessSession;
import ru.solomentsev.aqa.process.DefaultProcessLauncher;
import ru.solomentsev.aqa.process.DockerProcessLauncher;
import ru.solomentsev.aqa.process.ProcessLauncher;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

/**
 * Читает настройки запуска MCP-сервера из system properties и environment.
 *
 * <p>System properties имеют приоритет. Поддерживаются два режима:
 * {@code local} запускает JAR через Java, {@code docker} создаёт отдельный
 * контейнер из заранее собранного image.</p>
 */
public final class McpServerConfiguration {

    private static final String DEFAULT_PROTOCOL_VERSION = "2025-11-25";
    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofSeconds(15);

    private final String mode;
    private final Path serverJar;
    private final String javaExecutable;
    private final Path workingDirectory;
    private final String dockerExecutable;
    private final String dockerImage;
    private final Path dockerEnvFile;
    private final String runId;
    private final String protocolVersion;
    private final Duration startupTimeout;
    private final int dockerStopTimeoutSeconds;

    private McpServerConfiguration(Map<String, String> environment, Properties properties) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(properties, "properties");

        mode = value(environment, properties, "aqa.server.mode", "AQA_SERVER_MODE", "local")
                .toLowerCase();
        serverJar = optionalPath(value(environment, properties,
                "aqa.server.jar", "AQA_SERVER_JAR", null));
        javaExecutable = value(environment, properties,
                "aqa.server.java", "AQA_SERVER_JAVA", defaultJavaExecutable());
        workingDirectory = optionalPath(value(environment, properties,
                "aqa.server.workingDirectory", "AQA_SERVER_WORKING_DIRECTORY", null));
        dockerExecutable = value(environment, properties,
                "aqa.docker.executable", "AQA_DOCKER_EXECUTABLE", "docker");
        dockerImage = value(environment, properties,
                "aqa.docker.image", "AQA_DOCKER_IMAGE", null);
        dockerEnvFile = optionalPath(value(environment, properties,
                "aqa.docker.envFile", "AQA_DOCKER_ENV_FILE", null));
        runId = createRunId(value(environment, properties,
                "aqa.run.id", "AQA_RUN_ID", "local"));
        protocolVersion = value(environment, properties,
                "aqa.mcp.protocolVersion", "AQA_MCP_PROTOCOL_VERSION", DEFAULT_PROTOCOL_VERSION);
        startupTimeout = Duration.ofSeconds(parsePositiveLong(value(environment, properties,
                "aqa.server.startupTimeoutSeconds", "AQA_SERVER_STARTUP_TIMEOUT_SECONDS", "15"),
                "startup timeout"));
        dockerStopTimeoutSeconds = Math.toIntExact(parsePositiveLong(value(environment, properties,
                "aqa.docker.stopTimeoutSeconds", "AQA_DOCKER_STOP_TIMEOUT_SECONDS", "10"),
                "Docker stop timeout"));

        if (!mode.equals("local") && !mode.equals("docker")) {
            throw new IllegalArgumentException("AQA_SERVER_MODE must be 'local' or 'docker'");
        }
        if (mode.equals("local") && serverJar == null) {
            throw new IllegalArgumentException("AQA_SERVER_JAR is required in local mode");
        }
        if (mode.equals("docker") && (dockerImage == null || dockerEnvFile == null)) {
            throw new IllegalArgumentException(
                    "AQA_DOCKER_IMAGE and AQA_DOCKER_ENV_FILE are required in docker mode");
        }
    }

    /**
     * Создаёт конфигурацию из текущего окружения JVM.
     *
     * @return проверенная конфигурация
     */
    public static McpServerConfiguration fromEnvironment() {
        return new McpServerConfiguration(System.getenv(), System.getProperties());
    }

    static McpServerConfiguration from(Map<String, String> environment, Properties properties) {
        return new McpServerConfiguration(environment, properties);
    }

    /**
     * Запускает тестовый экземпляр и выполняет MCP readiness handshake.
     *
     * @return готовая сессия
     * @throws IOException если процесс не удалось запустить
     */
    public McpProcessSession startSession() throws IOException {
        ProcessLauncher launcher;
        List<String> command;

        if (mode.equals("local")) {
            launcher = new DefaultProcessLauncher();
            command = List.of(javaExecutable, "-jar", serverJar.toString());
        } else {
            launcher = new DockerProcessLauncher(
                    dockerExecutable,
                    dockerImage,
                    dockerEnvFile,
                    runId,
                    dockerStopTimeoutSeconds
            );
            command = List.of();
        }

        return McpProcessSession.start(
                launcher,
                command,
                Map.of(),
                workingDirectory,
                protocolVersion,
                "atlassian-mcp-light-autotests",
                "1.0",
                startupTimeout
        );
    }

    /**
     * Возвращает выбранный режим запуска.
     *
     * @return {@code local} или {@code docker}
     */
    public String mode() {
        return mode;
    }

    private static String value(Map<String, String> environment,
                                Properties properties,
                                String propertyName,
                                String environmentName,
                                String defaultValue) {
        String property = properties.getProperty(propertyName);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        String variable = environment.get(environmentName);
        if (variable != null && !variable.isBlank()) {
            return variable.trim();
        }
        return defaultValue;
    }

    private static Path optionalPath(String value) {
        return value == null || value.isBlank()
                ? null
                : Path.of(value).toAbsolutePath().normalize();
    }

    private static String defaultJavaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static long parsePositiveLong(String value, String description) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) {
                throw new IllegalArgumentException(description + " must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(description + " must be a positive integer", exception);
        }
    }

    private static String createRunId(String base) {
        String safeBase = base.replaceAll("[^A-Za-z0-9_.-]", "-");
        if (safeBase.isBlank()) {
            safeBase = "run";
        }
        if (!Character.isLetterOrDigit(safeBase.charAt(0))) {
            safeBase = "run-" + safeBase;
        }
        if (safeBase.length() > 42) {
            safeBase = safeBase.substring(0, 42);
        }
        return safeBase + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
