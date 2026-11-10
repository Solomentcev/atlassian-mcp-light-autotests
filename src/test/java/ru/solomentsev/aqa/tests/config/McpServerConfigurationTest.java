package ru.solomentsev.aqa.tests.config;

import io.qameta.allure.Allure;
import ru.solomentsev.aqa.config.McpServerConfiguration;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("AQA Test Infrastructure")
    @Tag("unit")
    @Tag("configuration")
class McpServerConfigurationTest {

    @Test
    @Feature("Server Configuration")
    @Story("Чтение режима запуска из переменных окружения")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Чтение режима запуска из переменных окружения")
    @Description("Предусловия: Переданы AQA_SERVER_MODE=local и AQA_SERVER_JAR.\nШаги: построить конфигурацию из переменных окружения.\nОжидаемый результат: конфигурация выбирает режим local.")
    @Tag("configuration")
    void shouldReadLocalModeFromEnvironment() {
        Allure.step("Чтение режима запуска из переменных окружения", () -> {
            McpServerConfiguration configuration = McpServerConfiguration.from(
                    Map.of(
                            "AQA_SERVER_MODE", "local",
                            "AQA_SERVER_JAR", "server/target/server.jar"
                    ),
                    new Properties()
            );

            assertThat(configuration.mode()).isEqualTo("local");
        
        });
    }

    @Test
    @Feature("Server Configuration")
    @Story("Приоритет системных свойств над переменными окружения")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Приоритет системных свойств над переменными окружения")
    @Description("Предусловия: Environment задаёт local, system properties задают docker и параметры Docker.\nШаги: построить конфигурацию с обоими источниками.\nОжидаемый результат: системное свойство имеет приоритет; выбран режим docker.")
    @Tag("configuration")
    void shouldPreferSystemPropertiesToEnvironment() {
        Allure.step("Приоритет системных свойств над переменными окружения", () -> {
            Properties properties = new Properties();
            properties.setProperty("aqa.server.mode", "docker");
            properties.setProperty("aqa.docker.image", "atlassian-mcp-light:test");
            properties.setProperty("aqa.docker.envFile", ".runtime.env");

            McpServerConfiguration configuration = McpServerConfiguration.from(
                    Map.of(
                            "AQA_SERVER_MODE", "local",
                            "AQA_SERVER_JAR", "server.jar"
                    ),
                    properties
            );

            assertThat(configuration.mode()).isEqualTo("docker");
        
        });
    }

    @Test
    @Feature("Server Configuration")
    @Story("Проверка обязательного пути к JAR в local-режиме")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Проверка обязательного пути к JAR в local-режиме")
    @Description("Предусловия: задан local-режим, но AQA_SERVER_JAR отсутствует.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_SERVER_JAR.")
    @Tag("negative")
    void shouldRequireJarInLocalMode() {
        Allure.step("Проверка обязательного пути к JAR в local-режиме", () -> {
            assertThatThrownBy(() -> McpServerConfiguration.from(
                    Map.of("AQA_SERVER_MODE", "local"),
                    new Properties()
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("AQA_SERVER_JAR");
        
        });
    }

    @Test
    @Feature("Server Configuration")
    @Story("Проверка обязательных параметров Docker-режима")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Проверка обязательных параметров Docker-режима")
    @Description("Предусловия: задан Docker-режим и image, но отсутствует env-файл.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_DOCKER_ENV_FILE.")
    @Tag("negative")
    void shouldRequireImageAndEnvFileInDockerMode() {
        Allure.step("Проверка обязательных параметров Docker-режима", () -> {
            assertThatThrownBy(() -> McpServerConfiguration.from(
                    Map.of(
                            "AQA_SERVER_MODE", "docker",
                            "AQA_DOCKER_IMAGE", "atlassian-mcp-light:test"
                    ),
                    new Properties()
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("AQA_DOCKER_ENV_FILE");
        
        });
    }

    @Test
    @Feature("Server Configuration")
    @Story("Отклонение неизвестного режима запуска")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение неизвестного режима запуска")
    @Description("Предусловия: AQA_SERVER_MODE задан как неподдерживаемое значение remote.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_SERVER_MODE.")
    @Tag("negative")
    void shouldRejectUnknownMode() {
        Allure.step("Отклонение неизвестного режима запуска", () -> {
            assertThatThrownBy(() -> McpServerConfiguration.from(
                    Map.of("AQA_SERVER_MODE", "remote"),
                    new Properties()
            )).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("AQA_SERVER_MODE");
        
        });
    }
}
