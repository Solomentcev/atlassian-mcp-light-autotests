package ru.solomentsev.aqa.testdata;

import java.nio.file.Path;

/** Тестовые данные для проверки Docker launcher. */
public final class DockerTestData {

    private static final String EXECUTABLE = "docker";
    private static final String RUN_ID = "build_123";
    private static final String CONTAINER_NAME = "aml-" + RUN_ID;
    private static final Path ENV_FILE = Path.of(".runtime.env");
    private static final String IMAGE = "atlassian-mcp-light:latest";
    private static final String UNSAFE_RUN_ID = "unsafe/run-id";
    private static final String SAFE_RUN_ID = "run-123";
    private static final int STOP_TIMEOUT_SECONDS = 5;
    private static final int INVALID_STOP_TIMEOUT_SECONDS = 0;

    public static String getExecutable() {
        return EXECUTABLE;
    }

    public static String getRunId() {
        return RUN_ID;
    }

    public static String getContainerName() {
        return CONTAINER_NAME;
    }

    public static Path getEnvFile() {
        return ENV_FILE;
    }

    public static String getImage() {
        return IMAGE;
    }

    public static String getUnsafeRunId() {
        return UNSAFE_RUN_ID;
    }

    public static String getSafeRunId() {
        return SAFE_RUN_ID;
    }

    public static int getStopTimeoutSeconds() {
        return STOP_TIMEOUT_SECONDS;
    }

    public static int getInvalidStopTimeoutSeconds() {
        return INVALID_STOP_TIMEOUT_SECONDS;
    }

    private DockerTestData() {
    }
}
