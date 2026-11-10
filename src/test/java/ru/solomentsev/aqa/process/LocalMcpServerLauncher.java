package ru.solomentsev.aqa.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Запускает локальный исполняемый JAR тестируемого MCP-сервера через Java. */
public final class LocalMcpServerLauncher implements McpServerLauncher {

    private final String javaExecutable;
    private final Path serverJar;

    public LocalMcpServerLauncher(String javaExecutable, Path serverJar) {
        if (javaExecutable == null || javaExecutable.isBlank()) {
            throw new IllegalArgumentException("javaExecutable must not be blank");
        }
        this.javaExecutable = javaExecutable;
        this.serverJar = Objects.requireNonNull(serverJar, "serverJar")
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public ManagedProcess launch(Map<String, String> environment,
                                 Path workingDirectory) throws IOException {
        List<String> command = List.of(javaExecutable, "-jar", serverJar.toString());
        return new ProcessBuilderLauncher().launch(command, environment, workingDirectory);
    }
}