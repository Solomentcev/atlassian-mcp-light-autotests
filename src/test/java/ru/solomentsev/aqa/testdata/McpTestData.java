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
    public static final String CLASSIC_SESSION_MODE = "classic";
    public static final String STATELESS_SESSION_MODE = "stateless";
    public static final String MOCK_CLIENT_NAME = "aqa-tests";
    public static final String MOCK_CLIENT_VERSION = "1.0";
    public static final String SERVER_NAME = "atlassian-lite";
    public static final String SERVER_VERSION = "1.0.0";

    public static final String JSON_RPC_VERSION = "2.0";
    public static final String INITIALIZE_METHOD = "initialize";
    public static final String PING_METHOD = "ping";
    public static final String TOOLS_LIST_METHOD = "tools/list";
    public static final String INITIALIZED_NOTIFICATION_METHOD = "notifications/initialized";
    public static final String SERVER_DISCOVER_METHOD = "server/discover";
    public static final String TOOLS_CALL_METHOD = "tools/call";
    public static final String FAKE_CLASSIC_SERVER_NAME = "fake-mcp-server";
    public static final String FAKE_STATELESS_SERVER_NAME = "fake-stateless-server";
    public static final String FAKE_SERVER_VERSION = "1.0";

    public static final int JSON_RPC_PARSE_ERROR = -32700;
    public static final int JSON_RPC_INVALID_REQUEST = -32600;
    public static final int JSON_RPC_METHOD_NOT_FOUND = -32601;
    public static final int JSON_RPC_INVALID_PARAMS = -32602;
    public static final int MCP_UNSUPPORTED_PROTOCOL_VERSION = -32022;
    public static final int MCP_SERVER_NOT_INITIALIZED = -32001;

    public static final int EXPECTED_TOOL_COUNT = 13;
    public static final int PIPE_BUFFER_SIZE_BYTES = 8_192;
    public static final int STDERR_BUFFER_SIZE_BYTES = 8_192;
    public static final long DISCOVERY_TTL_MS = 300_000L;
    public static final String DISCOVERY_CACHE_SCOPE = "public";

    public static final int DEFAULT_LIMIT = 1;
    public static final int MIN_STRING_LENGTH = 1;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 100;
    public static final int INVALID_LIMIT_BELOW_MIN = 0;
    public static final int INVALID_LIMIT_ABOVE_MAX = 101;

    public static final long DEFAULT_STARTUP_TIMEOUT_MS = 15_000L;
    public static final long PROCESS_SHUTDOWN_TIMEOUT_MS = 3_000L;

    public static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMillis(DEFAULT_STARTUP_TIMEOUT_MS);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    public static final long SHORT_REQUEST_TIMEOUT_MS = 3_000L;
    public static final long ASYNC_EVENT_TIMEOUT_MS = 3_000L;
    public static final Duration SHORT_REQUEST_TIMEOUT = Duration.ofMillis(SHORT_REQUEST_TIMEOUT_MS);
    public static final Duration ASYNC_EVENT_TIMEOUT = Duration.ofMillis(ASYNC_EVENT_TIMEOUT_MS);
    public static final Duration INVALID_REQUEST_TIMEOUT = Duration.ofSeconds(1);
    public static final Duration JAVA_VERSION_PROCESS_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration STDERR_COLLECTION_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration PROCESS_SHUTDOWN_TIMEOUT = Duration.ofMillis(PROCESS_SHUTDOWN_TIMEOUT_MS);

    private McpTestData() {
    }
}
