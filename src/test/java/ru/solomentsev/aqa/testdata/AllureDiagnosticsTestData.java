package ru.solomentsev.aqa.testdata;

/** Тестовые строки и значения для проверки маскирования Allure-диагностики. */
public final class AllureDiagnosticsTestData {

    private static final String BASIC_CREDENTIAL = "dXNlcjp0b2tlbg";
    private static final String BEARER_CREDENTIAL = "secret-value";
    private static final String AUTHORIZATION_HEADERS =
            "Authorization: Basic " + BASIC_CREDENTIAL + "\nAuthorization: Bearer " + BEARER_CREDENTIAL;
    private static final String MASKED_BASIC_HEADER = "Authorization: Basic [REDACTED]";
    private static final String MASKED_BEARER_HEADER = "Authorization: Bearer [REDACTED]";

    private static final String TOKEN_VALUE = "token-value";
    private static final String PASSWORD_VALUE = "pass-value";
    private static final String SECRET_VALUE = "another-value";
    private static final String JSON_SECRET_VALUE = "json-secret";
    private static final String SENSITIVE_ENVIRONMENT_VALUES =
            "JIRA_TOKEN=" + TOKEN_VALUE + "\nAQA_PASSWORD=" + PASSWORD_VALUE
                    + "\nsecret: " + SECRET_VALUE + "\n{\"access_token\":\"" + JSON_SECRET_VALUE + "\"}";

    private static final String MASKED_TOKEN = "JIRA_TOKEN=[REDACTED]";
    private static final String MASKED_PASSWORD = "AQA_PASSWORD=[REDACTED]";
    private static final String MASKED_SECRET = "secret: [REDACTED]";
    private static final String MASKED_JSON_SECRET = "\"access_token\":\"[REDACTED]\"";
    private static final String NON_SENSITIVE_DIAGNOSTIC =
            "MCP server started; request handled successfully";
    private static final String REDACTED_MARKER = "[REDACTED]";

    private AllureDiagnosticsTestData() {
    }

    public static String getAuthorizationHeaders() {
        return AUTHORIZATION_HEADERS;
    }

    public static String getBasicCredential() {
        return BASIC_CREDENTIAL;
    }

    public static String getBearerCredential() {
        return BEARER_CREDENTIAL;
    }

    public static String getMaskedBasicHeader() {
        return MASKED_BASIC_HEADER;
    }

    public static String getMaskedBearerHeader() {
        return MASKED_BEARER_HEADER;
    }

    public static String getSensitiveEnvironmentValues() {
        return SENSITIVE_ENVIRONMENT_VALUES;
    }

    public static String getTokenValue() {
        return TOKEN_VALUE;
    }

    public static String getPasswordValue() {
        return PASSWORD_VALUE;
    }

    public static String getSecretValue() {
        return SECRET_VALUE;
    }

    public static String getJsonSecretValue() {
        return JSON_SECRET_VALUE;
    }

    public static String getMaskedToken() {
        return MASKED_TOKEN;
    }

    public static String getMaskedPassword() {
        return MASKED_PASSWORD;
    }

    public static String getMaskedSecret() {
        return MASKED_SECRET;
    }

    public static String getMaskedJsonSecret() {
        return MASKED_JSON_SECRET;
    }

    public static String getNonSensitiveDiagnostic() {
        return NON_SENSITIVE_DIAGNOSTIC;
    }

    public static String getRedactedMarker() {
        return REDACTED_MARKER;
    }
}
