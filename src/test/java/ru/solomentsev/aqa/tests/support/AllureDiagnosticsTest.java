package ru.solomentsev.aqa.tests.support;

import ru.solomentsev.aqa.support.diagnostics.AllureDiagnostics;
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
@Tag("regression")
class AllureDiagnosticsTest {

    @Test
    @Story("Маскирование Authorization headers")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Маскирование Authorization: Basic и Bearer credentials в диагностическом тексте")
    @Description("Проверяет, что диагностические вложения не раскрывают Basic и Bearer credentials.")
    void shouldMaskAuthorizationHeaders() {
        Allure.parameter("Проверяемые заголовки", "Authorization: Basic; Authorization: Bearer");
        Allure.parameter("Ожидаемый маркер маскирования", "[REDACTED]");
        String source = "Authorization: Basic dXNlcjp0b2tlbg\nAuthorization: Bearer secret-value";

        String masked = Allure.step("Замаскировать Basic и Bearer credentials", () -> AllureDiagnostics.maskSecrets(source));

        assertThat(masked)
                .as("Authorization Basic и Bearer должны быть замаскированы в диагностике")
                .contains("Authorization: Basic [REDACTED]")
                .contains("Authorization: Bearer [REDACTED]")
                .doesNotContain("dXNlcjp0b2tlbg", "secret-value");
    
    }

    @Test
    @Story("Маскирование секретных переменных окружения")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Маскирование значений переменных TOKEN, PASSWORD и SECRET")
    @Description("Проверяет маскирование секретов в строках диагностики до прикрепления к Allure.")
    void shouldMaskSensitiveEnvironmentValues() {
        Allure.parameter("Проверяемые секретные поля", "TOKEN; PASSWORD; SECRET; access_token");
        Allure.parameter("Ожидаемый маркер маскирования", "[REDACTED]");
        String source = "JIRA_TOKEN=token-value\nAQA_PASSWORD=pass-value\nsecret: another-value\n{\"access_token\":\"json-secret\"}";

        String masked = Allure.step("Замаскировать значения секретных переменных и JSON-полей", () -> AllureDiagnostics.maskSecrets(source));

        assertThat(masked)
                .as("Значения секретных переменных окружения и JSON-полей должны быть замаскированы")
                .contains("JIRA_TOKEN=[REDACTED]")
                .contains("AQA_PASSWORD=[REDACTED]")
                .contains("secret: [REDACTED]")
                .contains("\"access_token\":\"[REDACTED]\"")
                .doesNotContain("token-value", "pass-value", "another-value", "json-secret");
    
    }

    @Test
    @Story("Сохранение обычной диагностики")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Диагностика без секретов остаётся неизменной после маскирования")
    @Description("Проверяет, что маскирование не изменяет обычные сообщения STDERR.")
    void shouldPreserveNonSensitiveDiagnostics() {
        Allure.parameter("Тип диагностики", "обычное несекретное сообщение STDERR");
        String source = "MCP server started; request handled successfully";

        String masked = Allure.step("Проверить обработку обычной диагностики", () -> AllureDiagnostics.maskSecrets(source));
        assertThat(masked)
                .as("Обычные диагностические сообщения не должны изменяться маскированием")
                .isEqualTo(source);
    
    }
}
