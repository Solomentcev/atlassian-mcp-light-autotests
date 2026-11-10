package ru.solomentsev.aqa.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Запускает отдельный тестовый контейнер через Docker CLI.
 *
 * <p>STDIN, STDOUT и STDERR Docker CLI предоставляются MCP Client так же,
 * как потоки локального Java-процесса. Псевдотерминал не создаётся.</p>
 *
 * <p>Для cleanup используется уникальное имя контейнера текущего AQA run.
 * После завершения Docker CLI launcher проверяет наличие контейнера,
 * останавливает его при необходимости и удаляет принудительно, если
 * автоматическое удаление через {@code --rm} не сработало.</p>
 */
public final class DockerMcpServerLauncher implements McpServerLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(DockerMcpServerLauncher.class);
    private static final Pattern SAFE_RUN_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,58}");
    private static final Duration DOCKER_COMMAND_TIMEOUT = Duration.ofSeconds(15);

    private final String dockerExecutable;
    private final String image;
    private final Path envFile;
    private final String containerName;
    private final int stopTimeoutSeconds;

    public DockerMcpServerLauncher(String dockerExecutable,
                                 String image,
                                 Path envFile,
                                 String runId,
                                 int stopTimeoutSeconds) {
        this.dockerExecutable = requireText(dockerExecutable, "dockerExecutable");
        this.image = requireText(image, "image");
        this.envFile = Objects.requireNonNull(envFile, "envFile").toAbsolutePath().normalize();

        if (runId == null || !SAFE_RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException(
                    "runId must contain 1-59 safe characters: letters, digits, dot, underscore or hyphen");
        }
        if (stopTimeoutSeconds < 1) {
            throw new IllegalArgumentException("stopTimeoutSeconds must be positive");
        }

        this.containerName = "aml-" + runId;
        this.stopTimeoutSeconds = stopTimeoutSeconds;
    }

    @Override
    public ManagedProcess launch(Map<String, String> environment,
                                 Path workingDirectory) throws IOException {
        Objects.requireNonNull(environment, "environment");
        List<String> command = buildRunCommand(dockerExecutable, containerName, envFile, image);
        LOG.info("Запуск Docker-контейнера: name={}, image={}", containerName, image);
        return new ProcessBuilderLauncher().launch(
                command,
                environment,
                workingDirectory,
                this::cleanupContainer
        );
    }

    static List<String> buildRunCommand(String dockerExecutable,
                                        String containerName,
                                        Path envFile,
                                        String image) {
        List<String> command = new ArrayList<>();
        command.add(dockerExecutable);
        command.add("run");
        command.add("--rm");
        command.add("--name");
        command.add(containerName);
        command.add("--label");
        command.add("aqa.run-id=" + containerName.substring("aml-".length()));
        command.add("-i");
        command.add("--env-file");
        command.add(envFile.toAbsolutePath().normalize().toString());
        command.add(image);
        return List.copyOf(command);
    }

    private void cleanupContainer() throws IOException {
        LOG.info("Очистка Docker-контейнера: name={}", containerName);
        CommandResult inspect = executeDocker(
                "inspect",
                "--format={{index .Config.Labels \"aqa.run-id\"}}|{{.State.Running}}",
                containerName
        );

        if (inspect.exitCode() != 0) {
            String output = inspect.output().toLowerCase();
            if (output.contains("no such object") || output.contains("no such container")) {
                return;
            }
            throw new IOException("Failed to inspect Docker container " + containerName
                    + ": " + inspect.output());
        }

        String[] state = inspect.output().trim().split("\\|", -1);
        String expectedRunId = containerName.substring("aml-".length());
        if (state.length != 2 || !expectedRunId.equals(state[0])) {
            throw new IOException("Docker container label does not match AQA run; refusing cleanup: "
                    + containerName);
        }

        if ("true".equalsIgnoreCase(state[1])) {
            LOG.debug("Остановка Docker-контейнера: name={}, timeout={}s", containerName, stopTimeoutSeconds);
            executeDocker("stop", "--time", Integer.toString(stopTimeoutSeconds), containerName);
        }

        CommandResult remove = executeDocker("rm", "-f", containerName);
        if (remove.exitCode() != 0 && !remove.output().toLowerCase().contains("no such container")) {
            throw new IOException("Failed to remove Docker container " + containerName
                    + ": " + remove.output());
        }
        LOG.info("Docker-контейнер очищен: name={}", containerName);
    }

    private CommandResult executeDocker(String... arguments) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(dockerExecutable);
        command.addAll(List.of(arguments));

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        try {
            if (!process.waitFor(DOCKER_COMMAND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Docker command timed out: " + String.join(" ", command));
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), output);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while executing Docker cleanup command", exception);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private record CommandResult(int exitCode, String output) {
    }
}