package ru.solomentsev.aqa.testdata;

import java.time.Duration;

/**
 * Общие параметры MCP-сценариев.
 *
 * <p>Значения собраны здесь, чтобы версии протокола и временные ограничения
 * не расходились между тестовыми классами.</p>
 */
public final class McpTestData {

    private static final String CLASSIC_PROTOCOL_VERSION = "2025-11-25";
    private static final String STATELESS_PROTOCOL_VERSION = "2026-07-28";
    private static final String LEGACY_PROTOCOL_VERSION = "2024-11-05";
    private static final String UNSUPPORTED_PROTOCOL_VERSION = "2099-01-01";
    private static final String CLIENT_NAME = "atlassian-mcp-light-autotests";
    private static final String CLIENT_VERSION = "1.0";
    private static final String CLASSIC_SESSION_MODE = "classic";
    private static final String STATELESS_SESSION_MODE = "stateless";
    private static final String MOCK_CLIENT_NAME = "aqa-tests";
    private static final String MOCK_CLIENT_VERSION = "1.0";
    private static final String SERVER_NAME = "atlassian-lite";
    private static final String SERVER_VERSION = "1.0.0";

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String INITIALIZE_METHOD = "initialize";
    private static final String PING_METHOD = "ping";
    private static final String TOOLS_LIST_METHOD = "tools/list";
    private static final String INITIALIZED_NOTIFICATION_METHOD = "notifications/initialized";
    private static final String SERVER_DISCOVER_METHOD = "server/discover";
    private static final String TOOLS_CALL_METHOD = "tools/call";
    private static final String FAKE_CLASSIC_SERVER_NAME = "fake-mcp-server";
    private static final String FAKE_STATELESS_SERVER_NAME = "fake-stateless-server";
    private static final String FAKE_SERVER_VERSION = "1.0";

    private static final int JSON_RPC_PARSE_ERROR = -32700;
    private static final int JSON_RPC_INVALID_REQUEST = -32600;
    private static final int JSON_RPC_METHOD_NOT_FOUND = -32601;
    private static final int JSON_RPC_INVALID_PARAMS = -32602;
    private static final int MCP_UNSUPPORTED_PROTOCOL_VERSION = -32022;
    private static final int MCP_SERVER_NOT_INITIALIZED = -32001;

    private static final int EXPECTED_TOOL_COUNT = 13;
    private static final int PIPE_BUFFER_SIZE_BYTES = 8_192;
    private static final int STDERR_BUFFER_SIZE_BYTES = 8_192;
    private static final long DISCOVERY_TTL_MS = 300_000L;
    private static final String DISCOVERY_CACHE_SCOPE = "public";

    private static final int DEFAULT_LIMIT = 1;
    private static final int MIN_STRING_LENGTH = 1;
    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 100;
    private static final int INVALID_LIMIT_BELOW_MIN = 0;
    private static final int INVALID_LIMIT_ABOVE_MAX = 101;

    private static final long DEFAULT_STARTUP_TIMEOUT_MS = 15_000L;
    private static final long PROCESS_SHUTDOWN_TIMEOUT_MS = 3_000L;

    private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMillis(DEFAULT_STARTUP_TIMEOUT_MS);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long SHORT_REQUEST_TIMEOUT_MS = 3_000L;
    private static final long ASYNC_EVENT_TIMEOUT_MS = 3_000L;
    private static final Duration SHORT_REQUEST_TIMEOUT = Duration.ofMillis(SHORT_REQUEST_TIMEOUT_MS);
    private static final Duration ASYNC_EVENT_TIMEOUT = Duration.ofMillis(ASYNC_EVENT_TIMEOUT_MS);
    private static final Duration INVALID_REQUEST_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration JAVA_VERSION_PROCESS_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration STDERR_COLLECTION_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration PROCESS_SHUTDOWN_TIMEOUT = Duration.ofMillis(PROCESS_SHUTDOWN_TIMEOUT_MS);

    public static String getServerDiscoverMethod() { return SERVER_DISCOVER_METHOD; }
    public static String getToolsListMethod() { return TOOLS_LIST_METHOD; }

    public static String getClassicProtocolVersion() {
        return CLASSIC_PROTOCOL_VERSION;
    }

    public static String getStatelessProtocolVersion() {
        return STATELESS_PROTOCOL_VERSION;
    }

    public static String getLegacyProtocolVersion() {
        return LEGACY_PROTOCOL_VERSION;
    }

    public static String getUnsupportedProtocolVersion() {
        return UNSUPPORTED_PROTOCOL_VERSION;
    }

    public static String getClientName() {
        return CLIENT_NAME;
    }

    public static String getClientVersion() {
        return CLIENT_VERSION;
    }

    public static String getClassicSessionMode() {
        return CLASSIC_SESSION_MODE;
    }

    public static String getStatelessSessionMode() {
        return STATELESS_SESSION_MODE;
    }

    public static String getMockClientName() {
        return MOCK_CLIENT_NAME;
    }

    public static String getMockClientVersion() {
        return MOCK_CLIENT_VERSION;
    }

    public static String getServerName() {
        return SERVER_NAME;
    }

    public static String getServerVersion() {
        return SERVER_VERSION;
    }

    public static String getJsonRpcVersion() {
        return JSON_RPC_VERSION;
    }

    public static String getInitializeMethod() {
        return INITIALIZE_METHOD;
    }

    public static String getPingMethod() {
        return PING_METHOD;
    }

    public static String getToolsListMethod() {
        return TOOLS_LIST_METHOD;
    }

    public static String getInitializedNotificationMethod() {
        return INITIALIZED_NOTIFICATION_METHOD;
    }

    public static String getServerDiscoverMethod() {
        return SERVER_DISCOVER_METHOD;
    }

    public static String getToolsCallMethod() {
        return TOOLS_CALL_METHOD;
    }

    public static String getFakeClassicServerName() {
        return FAKE_CLASSIC_SERVER_NAME;
    }

    public static String getFakeStatelessServerName() {
        return FAKE_STATELESS_SERVER_NAME;
    }

    public static String getFakeServerVersion() {
        return FAKE_SERVER_VERSION;
    }

    public static int getJsonRpcParseError() {
        return JSON_RPC_PARSE_ERROR;
    }

    public static int getJsonRpcInvalidRequest() {
        return JSON_RPC_INVALID_REQUEST;
    }

    public static int getJsonRpcMethodNotFound() {
        return JSON_RPC_METHOD_NOT_FOUND;
    }

    public static int getJsonRpcInvalidParams() {
        return JSON_RPC_INVALID_PARAMS;
    }

    public static int getMcpUnsupportedProtocolVersion() {
        return MCP_UNSUPPORTED_PROTOCOL_VERSION;
    }

    public static int getMcpServerNotInitialized() {
        return MCP_SERVER_NOT_INITIALIZED;
    }

    public static int getExpectedToolCount() {
        return EXPECTED_TOOL_COUNT;
    }

    public static int getPipeBufferSizeBytes() {
        return PIPE_BUFFER_SIZE_BYTES;
    }

    public static int getStderrBufferSizeBytes() {
        return STDERR_BUFFER_SIZE_BYTES;
    }

    public static long getDiscoveryTtlMs() {
        return DISCOVERY_TTL_MS;
    }

    public static String getDiscoveryCacheScope() {
        return DISCOVERY_CACHE_SCOPE;
    }

    public static int getDefaultLimit() {
        return DEFAULT_LIMIT;
    }

    public static int getMinStringLength() {
        return MIN_STRING_LENGTH;
    }

    public static int getMinLimit() {
        return MIN_LIMIT;
    }

    public static int getMaxLimit() {
        return MAX_LIMIT;
    }

    public static int getInvalidLimitBelowMin() {
        return INVALID_LIMIT_BELOW_MIN;
    }

    public static int getInvalidLimitAboveMax() {
        return INVALID_LIMIT_ABOVE_MAX;
    }

    public static long getDefaultStartupTimeoutMs() {
        return DEFAULT_STARTUP_TIMEOUT_MS;
    }

    public static long getProcessShutdownTimeoutMs() {
        return PROCESS_SHUTDOWN_TIMEOUT_MS;
    }

    public static Duration getDefaultStartupTimeout() {
        return DEFAULT_STARTUP_TIMEOUT;
    }

    public static Duration getRequestTimeout() {
        return REQUEST_TIMEOUT;
    }

    public static long getShortRequestTimeoutMs() {
        return SHORT_REQUEST_TIMEOUT_MS;
    }

    public static long getAsyncEventTimeoutMs() {
        return ASYNC_EVENT_TIMEOUT_MS;
    }

    public static Duration getShortRequestTimeout() {
        return SHORT_REQUEST_TIMEOUT;
    }

    public static Duration getAsyncEventTimeout() {
        return ASYNC_EVENT_TIMEOUT;
    }

    public static Duration getInvalidRequestTimeout() {
        return INVALID_REQUEST_TIMEOUT;
    }

    public static Duration getJavaVersionProcessTimeout() {
        return JAVA_VERSION_PROCESS_TIMEOUT;
    }

    public static Duration getStderrCollectionTimeout() {
        return STDERR_COLLECTION_TIMEOUT;
    }

    public static Duration getProcessShutdownTimeout() {
        return PROCESS_SHUTDOWN_TIMEOUT;
    }

    private McpTestData() {
    }
}
