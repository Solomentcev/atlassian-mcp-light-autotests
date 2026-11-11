package ru.solomentsev.aqa.tests.config;

import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ru.solomentsev.aqa.config.McpServerConfiguration;
import ru.solomentsev.aqa.testdata.McpConfigurationTestData;

import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThat;
import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThatThrownBy;

@Epic("AQA Test Infrastructure")
@Tag("unit")
@Tag("configuration")
@Tag("regression")
class McpServerConfigurationTest {

    @Test
    @Feature("Server Configuration")
    @Story("Чтение режима запуска из переменных окружения")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("AQA_SERVER_MODE=local и AQA_SERVER_JAR=server/target/server.jar из environment выбирают local mode")
    @Description("Предусловия: Переданы AQA_SERVER_MODE=local и AQA_SERVER_JAR.\nШаги: построить конфигурацию из переменных окружения.\nОжидаемый результат: конфигурация выбирает режим local.")
    void shouldReadLocalModeFromEnvironment() {
        Allure.parameter(McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getLocalMode());
        Allure.parameter(McpConfigurationTestData.getServerJarEnv(), McpConfigurationTestData.getServerJarPath());
        McpServerConfiguration configuration = Allure.step("Построить конфигурацию: " + McpConfigurationTestData.getServerModeEnv() + "="
                + McpConfigurationTestData.getLocalMode() + ", " + McpConfigurationTestData.getServerJarEnv() + " задан", () -> McpServerConfiguration.from(
                Map.of(
                        McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getLocalMode(),
                        McpConfigurationTestData.getServerJarEnv(), McpConfigurationTestData.getServerJarPath()
                ),
                new Properties()
        ));

        assertThat(configuration.mode()).as("При AQA_SERVER_MODE=local конфигурация должна выбрать local mode").isEqualTo(McpConfigurationTestData.getLocalMode());

    }

    @Test
    @Feature("Server Configuration")
    @Story("Загрузка настроек из dotenv-файла")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Параметры AQA_SERVER_MODE и AQA_SERVER_JAR автоматически загружаются из .env")
    @Description("Предусловия: Во временном каталоге создан .env с настройками local mode.\nШаги: построить конфигурацию с dotenv-файлом.\nОжидаемый результат: конфигурация выбирает local mode.")
    void shouldLoadConfigurationFromEnvFile(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, String.join(System.lineSeparator(),
                "# Локальный запуск AQA",
                McpConfigurationTestData.getServerModeEnv() + "=" + McpConfigurationTestData.getLocalMode(),
                McpConfigurationTestData.getServerJarEnv() + "=\"" + McpConfigurationTestData.getServerJarPath()
                        + "\" # путь к артефакту"));

        McpServerConfiguration configuration = Allure.step(
                "Загрузить настройки из временного .env-файла",
                () -> McpServerConfiguration.fromEnvironment(envFile, Map.of(), new Properties())
        );

        assertThat(configuration.mode())
                .as("AQA_SERVER_MODE из .env должен выбирать local mode")
                .isEqualTo(McpConfigurationTestData.getLocalMode());
    }

    @Test
    @Feature("Server Configuration")
    @Story("Приоритет системных переменных окружения над .env")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("AQA_SERVER_MODE из environment переопределяет значение из .env")
    @Description("Предусловия: .env задаёт local mode и JAR; environment задаёт docker mode и обязательные параметры Docker.\nШаги: построить конфигурацию из обоих источников.\nОжидаемый результат: используется значение environment.")
    void shouldPreferEnvironmentToEnvFile(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, String.join(System.lineSeparator(),
                McpConfigurationTestData.getServerModeEnv() + "=" + McpConfigurationTestData.getLocalMode(),
                McpConfigurationTestData.getServerJarEnv() + "=" + McpConfigurationTestData.getServerJarName(),
                McpConfigurationTestData.getDockerImageEnv() + "=" + McpConfigurationTestData.getDockerImage(),
                McpConfigurationTestData.getDockerEnvFileEnv() + "=" + McpConfigurationTestData.getDockerEnvFile()));

        McpServerConfiguration configuration = Allure.step(
                "Объединить .env с системными переменными окружения",
                () -> McpServerConfiguration.fromEnvironment(
                        envFile,
                        Map.of(McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getDockerMode()),
                        new Properties()
                )
        );

        assertThat(configuration.mode())
                .as("Системная переменная AQA_SERVER_MODE должна переопределять значение из .env")
                .isEqualTo(McpConfigurationTestData.getDockerMode());
    }

    @Test
    @Feature("Server Configuration")
    @Story("Приоритет системных свойств над переменными окружения")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("System property aqa.server.mode=docker переопределяет environment AQA_SERVER_MODE=local [image=atlassian-mcp-light:test]")
    @Description("Предусловия: Environment задаёт local, system properties задают docker и параметры Docker.\nШаги: построить конфигурацию с обоими источниками.\nОжидаемый результат: системное свойство имеет приоритет; выбран режим docker.")
    void shouldPreferSystemPropertiesToEnvironment() {
        Allure.parameter("Environment " + McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getLocalMode());
        Allure.parameter("System property " + McpConfigurationTestData.getServerModeProperty(), McpConfigurationTestData.getDockerMode());
        Allure.parameter("Docker image", McpConfigurationTestData.getDockerImage());
        Allure.parameter("Docker env file", McpConfigurationTestData.getDockerEnvFile());
        Properties properties = new Properties();
        properties.setProperty(McpConfigurationTestData.getServerModeProperty(), McpConfigurationTestData.getDockerMode());
        properties.setProperty(McpConfigurationTestData.getDockerImageProperty(), McpConfigurationTestData.getDockerImage());
        properties.setProperty(McpConfigurationTestData.getDockerEnvFileProperty(), McpConfigurationTestData.getDockerEnvFile());

        McpServerConfiguration configuration = Allure.step("Построить конфигурацию: environment=local, system property= docker", () -> McpServerConfiguration.from(
                Map.of(
                        McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getLocalMode(),
                        McpConfigurationTestData.getServerJarEnv(), McpConfigurationTestData.getServerJarName()
                ),
                properties
        ));

        assertThat(configuration.mode()).as("Системное свойство должно иметь приоритет и выбирать docker mode").isEqualTo(McpConfigurationTestData.getDockerMode());

    }

    @Test
    @Feature("Server Configuration")
    @Story("Проверка обязательного пути к JAR в local-режиме")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("AQA_SERVER_MODE=local без AQA_SERVER_JAR отклоняется")
    @Description("Предусловия: задан local-режим, но AQA_SERVER_JAR отсутствует.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_SERVER_JAR.")
    @Tag("negative")
    void shouldRequireJarInLocalMode() {
        Allure.parameter(McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getLocalMode());
        Allure.parameter(McpConfigurationTestData.getServerJarEnv(), "отсутствует");
        assertThatThrownBy(() -> Allure.step("Построить конфигурацию: AQA_SERVER_MODE=local, AQA_SERVER_JAR отсутствует", () -> McpServerConfiguration.from(
                Map.of(McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getLocalMode()),
                new Properties()
        ))).as("При AQA_SERVER_MODE=local без AQA_SERVER_JAR ожидается IllegalArgumentException с именем AQA_SERVER_JAR")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(McpConfigurationTestData.getServerJarEnv());

    }

    @Test
    @Feature("Server Configuration")
    @Story("Проверка обязательных параметров Docker-режима")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Docker mode с image=atlassian-mcp-light:test без AQA_DOCKER_ENV_FILE отклоняется")
    @Description("Предусловия: задан Docker-режим и image, но отсутствует env-файл.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_DOCKER_ENV_FILE.")
    @Tag("negative")
    void shouldRequireImageAndEnvFileInDockerMode() {
        Allure.parameter(McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getDockerMode());
        Allure.parameter(McpConfigurationTestData.getDockerImageEnv(), McpConfigurationTestData.getDockerImage());
        Allure.parameter(McpConfigurationTestData.getDockerEnvFileEnv(), "отсутствует");
        assertThatThrownBy(() -> Allure.step("Построить конфигурацию: docker image задан, AQA_DOCKER_ENV_FILE отсутствует", () -> McpServerConfiguration.from(
                Map.of(
                        McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getDockerMode(),
                        McpConfigurationTestData.getDockerImageEnv(), McpConfigurationTestData.getDockerImage()
                ),
                new Properties()
        ))).as("При AQA_SERVER_MODE=docker без AQA_DOCKER_ENV_FILE ожидается IllegalArgumentException")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(McpConfigurationTestData.getDockerEnvFileEnv());

    }

    @Test
    @Feature("Server Configuration")
    @Story("Отклонение неизвестного режима запуска")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение неподдерживаемого AQA_SERVER_MODE=remote")
    @Description("Предусловия: AQA_SERVER_MODE задан как неподдерживаемое значение remote.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_SERVER_MODE.")
    @Tag("negative")
    void shouldRejectUnknownMode() {
        Allure.parameter(McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getUnsupportedMode());
        assertThatThrownBy(() -> Allure.step("Построить конфигурацию с неподдерживаемым AQA_SERVER_MODE=remote", () -> McpServerConfiguration.from(
                Map.of(McpConfigurationTestData.getServerModeEnv(), McpConfigurationTestData.getUnsupportedMode()),
                new Properties()
        ))).as("При AQA_SERVER_MODE=remote ожидается IllegalArgumentException с именем AQA_SERVER_MODE")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(McpConfigurationTestData.getServerModeEnv());

    }
}
