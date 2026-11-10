package ru.solomentsev.aqa.testdata;

import java.time.Duration;

/**
 * Общие параметры MCP-сценариев.
 *
 * <p>Значения собраны здесь, чтобы версии протокола и временные ограничения
 * не расходились между тестовыми классами.</p>
 */
public final class McpTestData {

    public static final String CLASSIC_PROTOCOL_VERSION = "2025-11-25";
    public static final String STATELESS_PROTOCOL_VERSION = "2026-07-28";
    public static final String LEGACY_PROTOCOL_VERSION = "2024-11-05";
    public static final String UNSUPPORTED_PROTOCOL_VERSION = "2099-01-01";
    public static final String CLIENT_NAME = "atlassian-mcp-light-autotests";
    public static final String CLIENT_VERSION = "1.0";
    public static final String MOCK_CLIENT_NAME = "aqa-tests";
    public static final String MOCK_CLIENT_VERSION = "1.0";
    public static final String SERVER_NAME = "atlassian-lite";
    public static final String SERVER_VERSION = "1.0.0";

    public static final int EXPECTED_TOOL_COUNT = 13;
    public static final int DISCOVERY_TTL_MS = 300_000;
    public static final String DISCOVERY_CACHE_SCOPE = "public";

    public static final int DEFAULT_LIMIT = 1;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 100;
    public static final int INVALID_LIMIT_BELOW_MIN = 0;
    public static final int INVALID_LIMIT_ABOVE_MAX = 101;

    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration SHORT_REQUEST_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration ASYNC_EVENT_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration INVALID_REQUEST_TIMEOUT = Duration.ofSeconds(1);
    public static final Duration JAVA_VERSION_PROCESS_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration STDERR_COLLECTION_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration PROCESS_SHUTDOWN_TIMEOUT = Duration.ofSeconds(3);

    private McpTestData() {
    }
}
