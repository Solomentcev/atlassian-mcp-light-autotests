package ru.solomentsev.aqa.mcp;

/**
 * Ошибка обмена MCP Client с тестируемым процессом по STDIO.
 */
public class McpClientException extends RuntimeException {

    public McpClientException(String message) {
        super(message);
    }

    public McpClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
