package ru.solomentsev.aqa.testdata;

/** Тестовые значения для проверки MCP Client на изолированном STDIO endpoint. */
public final class McpClientTestData {

    private static final String FILTER_VALUE = "all";
    private static final boolean ALIVE_VALUE = true;
    private static final boolean EXPERIMENTAL_CAPABILITY_ENABLED = true;

    private McpClientTestData() {
    }

    public static String getFilterValue() {
        return FILTER_VALUE;
    }

    public static boolean isAliveValue() {
        return ALIVE_VALUE;
    }

    public static boolean isExperimentalCapabilityEnabled() {
        return EXPERIMENTAL_CAPABILITY_ENABLED;
    }
}
