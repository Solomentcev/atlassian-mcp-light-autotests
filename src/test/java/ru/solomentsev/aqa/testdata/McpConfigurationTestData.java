package ru.solomentsev.aqa.testdata;

/** Тестовые данные для проверки конфигурации запуска MCP-сервера. */
public final class McpConfigurationTestData {

    public static final String SERVER_MODE_ENV = "AQA_SERVER_MODE";
    public static final String SERVER_JAR_ENV = "AQA_SERVER_JAR";
    public static final String DOCKER_IMAGE_ENV = "AQA_DOCKER_IMAGE";
    public static final String DOCKER_ENV_FILE_ENV = "AQA_DOCKER_ENV_FILE";

    public static final String SERVER_MODE_PROPERTY = "aqa.server.mode";
    public static final String DOCKER_IMAGE_PROPERTY = "aqa.docker.image";
    public static final String DOCKER_ENV_FILE_PROPERTY = "aqa.docker.envFile";

    public static final String LOCAL_MODE = "local";
    public static final String DOCKER_MODE = "docker";
    public static final String UNSUPPORTED_MODE = "remote";

    public static final String SERVER_JAR_PATH = "server/target/server.jar";
    public static final String SERVER_JAR_NAME = "server.jar";
    public static final String DOCKER_IMAGE = "atlassian-mcp-light:test";
    public static final String DOCKER_ENV_FILE = ".runtime.env";

    private McpConfigurationTestData() {
    }
}
