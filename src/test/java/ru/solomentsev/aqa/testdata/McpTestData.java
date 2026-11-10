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
    public static final String CLIENT_NAME = "atlassian-mcp-light-autotests";
    public static final String CLIENT_VERSION = "1.0";

    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration SHORT_REQUEST_TIMEOUT = Duration.ofSeconds(3);
    public static final Duration PROCESS_STARTUP_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration PROCESS_SHUTDOWN_TIMEOUT = Duration.ofSeconds(3);

    private McpTestData() {
    }
}
