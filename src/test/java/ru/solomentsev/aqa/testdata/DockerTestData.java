package ru.solomentsev.aqa.testdata;

import java.nio.file.Path;

/** Тестовые данные для проверки Docker launcher. */
public final class DockerTestData {

    public static final String EXECUTABLE = "docker";
    public static final String RUN_ID = "build_123";
    public static final String CONTAINER_NAME = "aml-" + RUN_ID;
    public static final Path ENV_FILE = Path.of(".runtime.env");
    public static final String IMAGE = "atlassian-mcp-light:latest";
    public static final String UNSAFE_RUN_ID = "unsafe/run-id";
    public static final int STOP_TIMEOUT_SECONDS = 5;

    private DockerTestData() {
    }
}
