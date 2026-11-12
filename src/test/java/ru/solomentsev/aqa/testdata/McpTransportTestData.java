package ru.solomentsev.aqa.testdata;

/** Тестовые входные строки и JSON-RPC запросы для проверки STDIO transport. */
public final class McpTransportTestData {

    private static final String EMPTY_LINE = "";
    private static final String WHITESPACE_LINE = "   ";
    private static final String TRAILING_TOKEN_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"ping\"} trailing";
    private static final String MALFORMED_JSON_LINE = "{invalid-json";
    private static final String NON_OBJECT_JSON_ROOT = "[]";
    private static final String MISSING_METHOD_REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1}";
    private static final String MISSING_ID_REQUEST =
            "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"}";
    private static final String UNKNOWN_METHOD = "aqa/unknown-method";
    private static final String UNKNOWN_METHOD_UTF8 = "aqa/неизвестный-метод";

    private McpTransportTestData() {
    }

    public static String getEmptyLine() {
        return EMPTY_LINE;
    }

    public static String getWhitespaceLine() {
        return WHITESPACE_LINE;
    }

    public static String getTrailingTokenRequest() {
        return TRAILING_TOKEN_REQUEST;
    }

    public static String getMalformedJsonLine() {
        return MALFORMED_JSON_LINE;
    }

    public static String getNonObjectJsonRoot() {
        return NON_OBJECT_JSON_ROOT;
    }

    public static String getMissingMethodRequest() {
        return MISSING_METHOD_REQUEST;
    }

    public static String getMissingIdRequest() {
        return MISSING_ID_REQUEST;
    }

    public static String getUnknownMethod() {
        return UNKNOWN_METHOD;
    }

    public static String getUnknownMethodUtf8() {
        return UNKNOWN_METHOD_UTF8;
    }
}
