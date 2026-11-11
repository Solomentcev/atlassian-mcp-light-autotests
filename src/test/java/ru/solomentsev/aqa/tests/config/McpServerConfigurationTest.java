package ru.solomentsev.aqa.tests.config;

import ru.solomentsev.aqa.config.McpServerConfiguration;
import ru.solomentsev.aqa.testdata.McpConfigurationTestData;
import io.qameta.allure.Allure;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

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
    @Description("Предусловия: Переданы AQA_SERVER_MODE=local и AQA_SERVER_JAR.\nШаги: построить конфигурацию из переменных окружения.\nОжидаемый результат: конфигурация выбирает режим local.")    void shouldReadLocalModeFromEnvironment() {
        Allure.parameter(McpConfigurationTestData.SERVER_MODE_ENV, McpConfigurationTestData.LOCAL_MODE);
        Allure.parameter(McpConfigurationTestData.SERVER_JAR_ENV, McpConfigurationTestData.SERVER_JAR_PATH);
        McpServerConfiguration configuration = Allure.step("Построить конфигурацию: AQA_SERVER_MODE=local, AQA_SERVER_JAR задан", () -> McpServerConfiguration.from(
                Map.of(
                        McpConfigurationTestData.SERVER_MODE_ENV, McpConfigurationTestData.LOCAL_MODE,
                        McpConfigurationTestData.SERVER_JAR_ENV, McpConfigurationTestData.SERVER_JAR_PATH
                ),
                new Properties()
        ));

        assertThat(configuration.mode()).as("При AQA_SERVER_MODE=local конфигурация должна выбрать local mode").isEqualTo(McpConfigurationTestData.LOCAL_MODE);
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Загрузка настроек из dotenv-файла")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Параметры AQA_SERVER_MODE и AQA_SERVER_JAR автоматически загружаются из .env")
    @Description("Предусловия: Во временном каталоге создан .env с настройками local mode.\nШаги: построить конфигурацию с dotenv-файлом.\nОжидаемый результат: конфигурация выбирает local mode.")
    void shouldLoadConfigurationFromEnvFile(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                # Локальный запуск AQA
                AQA_SERVER_MODE=local
                AQA_SERVER_JAR="server/target/server.jar" # путь к артефакту
                """);

        McpServerConfiguration configuration = Allure.step(
                "Загрузить настройки из временного .env-файла",
                () -> McpServerConfiguration.fromEnvironment(envFile, Map.of(), new Properties())
        );

        assertThat(configuration.mode())
                .as("AQA_SERVER_MODE из .env должен выбирать local mode")
                .isEqualTo(McpConfigurationTestData.LOCAL_MODE);
    }

    @Test
    @Feature("Server Configuration")
    @Story("Приоритет системных переменных окружения над .env")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("AQA_SERVER_MODE из environment переопределяет значение из .env")
    @Description("Предусловия: .env задаёт local mode и JAR; environment задаёт docker mode и обязательные параметры Docker.\nШаги: построить конфигурацию из обоих источников.\nОжидаемый результат: используется значение environment.")
    void shouldPreferEnvironmentToEnvFile(@TempDir Path tempDir) throws IOException {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                AQA_SERVER_MODE=local
                AQA_SERVER_JAR=server.jar
                AQA_DOCKER_IMAGE=atlassian-mcp-light:from-env-file
                AQA_DOCKER_ENV_FILE=.runtime.env
                """);

        McpServerConfiguration configuration = Allure.step(
                "Объединить .env с системными переменными окружения",
                () -> McpServerConfiguration.fromEnvironment(
                        envFile,
                        Map.of(McpConfigurationTestData.SERVER_MODE_ENV, McpConfigurationTestData.DOCKER_MODE),
                        new Properties()
                )
        );

        assertThat(configuration.mode())
                .as("Системная переменная AQA_SERVER_MODE должна переопределять значение из .env")
                .isEqualTo(McpConfigurationTestData.DOCKER_MODE);
    }

    @Test
    @Feature("Server Configuration")
    @Story("Приоритет системных свойств над переменными окружения")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("System property aqa.server.mode=docker переопределяет environment AQA_SERVER_MODE=local [image=atlassian-mcp-light:test]")
    @Description("Предусловия: Environment задаёт local, system properties задают docker и параметры Docker.\nШаги: построить конфигурацию с обоими источниками.\nОжидаемый результат: системное свойство имеет приоритет; выбран режим docker.")    void shouldPreferSystemPropertiesToEnvironment() {
        Allure.parameter("Environment AQA_SERVER_MODE", McpConfigurationTestData.LOCAL_MODE);
        Allure.parameter("System property aqa.server.mode", McpConfigurationTestData.DOCKER_MODE);
        Allure.parameter("Docker image", McpConfigurationTestData.DOCKER_IMAGE);
        Allure.parameter("Docker env file", McpConfigurationTestData.DOCKER_ENV_FILE);
        Properties properties = new Properties();
        properties.setProperty(McpConfigurationTestData.SERVER_MODE_PROPERTY, McpConfigurationTestData.DOCKER_MODE);
        properties.setProperty(McpConfigurationTestData.DOCKER_IMAGE_PROPERTY, McpConfigurationTestData.DOCKER_IMAGE);
        properties.setProperty(McpConfigurationTestData.DOCKER_ENV_FILE_PROPERTY, McpConfigurationTestData.DOCKER_ENV_FILE);

        McpServerConfiguration configuration = Allure.step("Построить конфигурацию: environment=local, system property= docker", () -> McpServerConfiguration.from(
                Map.of(
                        McpConfigurationTestData.SERVER_MODE_ENV, McpConfigurationTestData.LOCAL_MODE,
                        McpConfigurationTestData.SERVER_JAR_ENV, McpConfigurationTestData.SERVER_JAR_NAME
                ),
                properties
        ));

        assertThat(configuration.mode()).as("Системное свойство должно иметь приоритет и выбирать docker mode").isEqualTo(McpConfigurationTestData.DOCKER_MODE);
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Проверка обязательного пути к JAR в local-режиме")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("AQA_SERVER_MODE=local без AQA_SERVER_JAR отклоняется")
    @Description("Предусловия: задан local-режим, но AQA_SERVER_JAR отсутствует.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_SERVER_JAR.")
    @Tag("negative")
    void shouldRequireJarInLocalMode() {
        Allure.parameter(McpConfigurationTestData.SERVER_MODE_ENV, McpConfigurationTestData.LOCAL_MODE);
        Allure.parameter(McpConfigurationTestData.SERVER_JAR_ENV, "отсутствует");
        assertThatThrownBy(() -> Allure.step("Построить конфигурацию: AQA_SERVER_MODE=local, AQA_SERVER_JAR отсутствует", () -> McpServerConfiguration.from(
                Map.of(McpConfigurationTestData.SERVER_MODE_ENV, McpConfigurationTestData.LOCAL_MODE),
                new Properties()
        ))).as("При AQA_SERVER_MODE=local без AQA_SERVER_JAR ожидается IllegalArgumentException с именем AQA_SERVER_JAR")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(McpConfigurationTestData.SERVER_JAR_ENV);
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Проверка обязательных параметров Docker-режима")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Docker mode с image=atlassian-mcp-light:test без AQA_DOCKER_ENV_FILE отклоняется")
    @Description("Предусловия: задан Docker-режим и image, но отсутствует env-файл.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_DOCKER_ENV_FILE.")
    @Tag("negative")
    void shouldRequireImageAndEnvFileInDockerMode() {
        Allure.parameter(McpConfigurationTestData.SERVER_MODE_ENV, McpConfigurationTestData.DOCKER_MODE);
        Allure.parameter(McpConfigurationTestData.DOCKER_IMAGE_ENV, McpConfigurationTestData.DOCKER_IMAGE);
        Allure.parameter(McpConfigurationTestData.DOCKER_ENV_FILE_ENV, "отсутствует");
        assertThatThrownBy(() -> Allure.step("Построить конфигурацию: docker image задан, AQA_DOCKER_ENV_FILE отсутствует", () -> McpServerConfiguration.from(
                Map.of(
                        McpConfigurationTestData.SERVER_MODE_ENV, McpConfigurationTestData.DOCKER_MODE,
                        McpConfigurationTestData.DOCKER_IMAGE_ENV, McpConfigurationTestData.DOCKER_IMAGE
                ),
                new Properties()
        ))).as("При AQA_SERVER_MODE=docker без AQA_DOCKER_ENV_FILE ожидается IllegalArgumentException")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(McpConfigurationTestData.DOCKER_ENV_FILE_ENV);
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Отклонение неизвестного режима запуска")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение неподдерживаемого AQA_SERVER_MODE=remote")
    @Description("Предусловия: AQA_SERVER_MODE задан как неподдерживаемое значение remote.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_SERVER_MODE.")
    @Tag("negative")
    void shouldRejectUnknownMode() {
        Allure.parameter(McpConfigurationTestData.SERVER_MODE_ENV, McpConfigurationTestData.UNSUPPORTED_MODE);
        assertThatThrownBy(() -> Allure.step("Построить конфигурацию с неподдерживаемым AQA_SERVER_MODE=remote", () -> McpServerConfiguration.from(
                Map.of(McpConfigurationTestData.SERVER_MODE_ENV, McpConfigurationTestData.UNSUPPORTED_MODE),
                new Properties()
        ))).as("При AQA_SERVER_MODE=remote ожидается IllegalArgumentException с именем AQA_SERVER_MODE")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(McpConfigurationTestData.SERVER_MODE_ENV);
    
    
    }
}
