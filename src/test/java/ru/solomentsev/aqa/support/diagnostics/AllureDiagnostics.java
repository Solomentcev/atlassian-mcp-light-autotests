package ru.solomentsev.aqa.support.diagnostics;

import io.qameta.allure.Allure;

/**
 * Добавляет в Allure безопасную диагностику тестового MCP-процесса.
 */
public final class AllureDiagnostics {

    private AllureDiagnostics() {
    }

    public static void attachStderr(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return;
        }
        Allure.addAttachment("MCP server STDERR (masked)", "text/plain", maskSecrets(stderr));
    }

    public static String maskSecrets(String value) {
        return value
                .replaceAll("(?i)(authorization\\s*[:=]\\s*basic\\s+)[A-Za-z0-9+/=]+", "$1[REDACTED]")
                .replaceAll("(?i)(authorization\\s*[:=]\\s*bearer\\s+)[^\\s,;]+", "$1[REDACTED]")
                .replaceAll("(?i)\\b((?:JIRA|CONFLUENCE|ATLASSIAN|AQA)_[A-Z0-9_]*(?:TOKEN|PASSWORD|SECRET|KEY))([\\s]*[:=][\\s]*)(\"[^\"]*\"|'[^']*'|[^\\s,;]+)", "$1$2[REDACTED]")
                .replaceAll("(?i)(\"[^\"]*(?:token|password|secret)[^\"]*\"\\s*:\\s*)(\"[^\"]*\"|'[^']*'|[^,\\s}]+)", "$1\"[REDACTED]\"")
                .replaceAll("(?i)\\b(token|password|secret)\\b([\\s]*[:=][\\s]*)(\"[^\"]*\"|'[^']*'|[^\\s,;]+)", "$1$2[REDACTED]");
    }
}
