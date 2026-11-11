package ru.solomentsev.aqa.tests.support;

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

import ru.solomentsev.aqa.support.diagnostics.AllureDiagnostics;
import ru.solomentsev.aqa.testdata.AllureDiagnosticsTestData;

import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThat;

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
        Allure.parameter("Ожидаемый маркер маскирования", AllureDiagnosticsTestData.getRedactedMarker());
        String source = AllureDiagnosticsTestData.getAuthorizationHeaders();

        String masked = Allure.step("Замаскировать Basic и Bearer credentials", () -> AllureDiagnostics.maskSecrets(source));

        assertThat(masked)
                .as("Authorization Basic и Bearer должны быть замаскированы в диагностике")
                .contains(AllureDiagnosticsTestData.getMaskedBasicHeader())
                .contains(AllureDiagnosticsTestData.getMaskedBearerHeader())
                .doesNotContain(AllureDiagnosticsTestData.getBasicCredential(), AllureDiagnosticsTestData.getBearerCredential());

    }

    @Test
    @Story("Маскирование секретных переменных окружения")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Маскирование значений переменных TOKEN, PASSWORD и SECRET")
    @Description("Проверяет маскирование секретов в строках диагностики до прикрепления к Allure.")
    void shouldMaskSensitiveEnvironmentValues() {
        Allure.parameter("Проверяемые секретные поля", "TOKEN; PASSWORD; SECRET; access_token");
        Allure.parameter("Ожидаемый маркер маскирования", AllureDiagnosticsTestData.getRedactedMarker());
        String source = AllureDiagnosticsTestData.getSensitiveEnvironmentValues();

        String masked = Allure.step("Замаскировать значения секретных переменных и JSON-полей", () -> AllureDiagnostics.maskSecrets(source));

        assertThat(masked)
                .as("Значения секретных переменных окружения и JSON-полей должны быть замаскированы")
                .contains(AllureDiagnosticsTestData.getMaskedToken())
                .contains(AllureDiagnosticsTestData.getMaskedPassword())
                .contains(AllureDiagnosticsTestData.getMaskedSecret())
                .contains(AllureDiagnosticsTestData.getMaskedJsonSecret())
                .doesNotContain(AllureDiagnosticsTestData.getTokenValue(), AllureDiagnosticsTestData.getPasswordValue(),
                        AllureDiagnosticsTestData.getSecretValue(), AllureDiagnosticsTestData.getJsonSecretValue());

    }

    @Test
    @Story("Сохранение обычной диагностики")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Диагностика без секретов остаётся неизменной после маскирования")
    @Description("Проверяет, что маскирование не изменяет обычные сообщения STDERR.")
    void shouldPreserveNonSensitiveDiagnostics() {
        Allure.parameter("Тип диагностики", "обычное несекретное сообщение STDERR");
        String source = AllureDiagnosticsTestData.getNonSensitiveDiagnostic();

        String masked = Allure.step("Проверить обработку обычной диагностики", () -> AllureDiagnostics.maskSecrets(source));
        assertThat(masked)
                .as("Обычные диагностические сообщения не должны изменяться маскированием")
                .isEqualTo(source);

    }
}
