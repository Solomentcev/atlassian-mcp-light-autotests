package ru.solomentsev.aqa.testdata;

/** Тестовые данные для проверки конфигурации запуска MCP-сервера. */
public final class McpConfigurationTestData {

    private static final String SERVER_MODE_ENV = "AQA_SERVER_MODE";
    private static final String SERVER_JAR_ENV = "AQA_SERVER_JAR";
    private static final String DOCKER_IMAGE_ENV = "AQA_DOCKER_IMAGE";
    private static final String DOCKER_ENV_FILE_ENV = "AQA_DOCKER_ENV_FILE";

    private static final String SERVER_MODE_PROPERTY = "aqa.server.mode";
    private static final String DOCKER_IMAGE_PROPERTY = "aqa.docker.image";
    private static final String DOCKER_ENV_FILE_PROPERTY = "aqa.docker.envFile";

    private static final String LOCAL_MODE = "local";
    private static final String DOCKER_MODE = "docker";
    private static final String UNSUPPORTED_MODE = "remote";

    private static final String SERVER_JAR_PATH = "server/target/server.jar";
    private static final String SERVER_JAR_NAME = "server.jar";
    private static final String DOCKER_IMAGE = "atlassian-mcp-light:test";
    private static final String DOCKER_ENV_FILE = ".runtime.env";

    public static String getServerModeEnv() {
        return SERVER_MODE_ENV;
    }

    public static String getServerJarEnv() {
        return SERVER_JAR_ENV;
    }

    public static String getDockerImageEnv() {
        return DOCKER_IMAGE_ENV;
    }

    public static String getDockerEnvFileEnv() {
        return DOCKER_ENV_FILE_ENV;
    }

    public static String getServerModeProperty() {
        return SERVER_MODE_PROPERTY;
    }

    public static String getDockerImageProperty() {
        return DOCKER_IMAGE_PROPERTY;
    }

    public static String getDockerEnvFileProperty() {
        return DOCKER_ENV_FILE_PROPERTY;
    }

    public static String getLocalMode() {
        return LOCAL_MODE;
    }

    public static String getDockerMode() {
        return DOCKER_MODE;
    }

    public static String getUnsupportedMode() {
        return UNSUPPORTED_MODE;
    }

    public static String getServerJarPath() {
        return SERVER_JAR_PATH;
    }

    public static String getServerJarName() {
        return SERVER_JAR_NAME;
    }

    public static String getDockerImage() {
        return DOCKER_IMAGE;
    }

    public static String getDockerEnvFile() {
        return DOCKER_ENV_FILE;
    }

    private McpConfigurationTestData() {
    }
}
