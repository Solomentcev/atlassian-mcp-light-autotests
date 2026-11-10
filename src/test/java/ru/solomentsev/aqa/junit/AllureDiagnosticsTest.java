package ru.solomentsev.aqa.junit;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("AQA Test Infrastructure")
@Feature("Allure Diagnostics")
@Tag("unit")
@Tag("allure")
class AllureDiagnosticsTest {

    @Test
    @Story("Маскирование Authorization headers")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Маскирование Basic и Bearer Authorization headers")
    @Description("Проверяет, что диагностические вложения не раскрывают Basic и Bearer credentials.")
    void shouldMaskAuthorizationHeaders() {
        Allure.step("Подготовить строки с тестовыми Authorization headers", () -> {
            String source = "Authorization: Basic dXNlcjp0b2tlbg\nAuthorization: Bearer secret-value";

            String masked = AllureDiagnostics.maskSecrets(source);

            assertThat(masked)
                    .contains("Authorization: Basic [REDACTED]")
                    .contains("Authorization: Bearer [REDACTED]")
                    .doesNotContain("dXNlcjp0b2tlbg", "secret-value");
        });
    }

    @Test
    @Story("Маскирование секретных переменных окружения")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Маскирование значений TOKEN, PASSWORD и SECRET")
    @Description("Проверяет маскирование секретов в строках диагностики до прикрепления к Allure.")
    void shouldMaskSensitiveEnvironmentValues() {
        Allure.step("Подготовить строки с тестовыми секретами", () -> {
            String source = "JIRA_TOKEN=token-value\nAQA_PASSWORD=pass-value\nsecret: another-value\n{\"access_token\":\"json-secret\"}";

            String masked = AllureDiagnostics.maskSecrets(source);

            assertThat(masked)
                    .contains("JIRA_TOKEN=[REDACTED]")
                    .contains("AQA_PASSWORD=[REDACTED]")
                    .contains("secret: [REDACTED]")
                    .contains("\"access_token\":\"[REDACTED]\"")
                    .doesNotContain("token-value", "pass-value", "another-value", "json-secret");
        });
    }

    @Test
    @Story("Сохранение обычной диагностики")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Сохранение несекретных диагностических сообщений")
    @Description("Проверяет, что маскирование не изменяет обычные сообщения STDERR.")
    void shouldPreserveNonSensitiveDiagnostics() {
        Allure.step("Проверить обычную диагностическую строку", () -> {
            String source = "MCP server started; request handled successfully";

            assertThat(AllureDiagnostics.maskSecrets(source)).isEqualTo(source);
        });
    }
}
