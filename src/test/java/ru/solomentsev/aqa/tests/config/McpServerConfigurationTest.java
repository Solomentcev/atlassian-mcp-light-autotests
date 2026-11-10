package ru.solomentsev.aqa.tests.config;

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
    @DisplayName("Чтение AQA_SERVER_MODE=local и AQA_SERVER_JAR из environment")
    @Description("Предусловия: Переданы AQA_SERVER_MODE=local и AQA_SERVER_JAR.\nШаги: построить конфигурацию из переменных окружения.\nОжидаемый результат: конфигурация выбирает режим local.")    void shouldReadLocalModeFromEnvironment() {
        McpServerConfiguration configuration = McpServerConfiguration.from(
                Map.of(
                        "AQA_SERVER_MODE", "local",
                        "AQA_SERVER_JAR", "server/target/server.jar"
                ),
                new Properties()
        );

        assertThat(configuration.mode()).as("При AQA_SERVER_MODE=local конфигурация должна выбрать local mode").isEqualTo("local");
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Приоритет системных свойств над переменными окружения")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Приоритет AQA_SERVER_MODE=docker из system properties над environment=local")
    @Description("Предусловия: Environment задаёт local, system properties задают docker и параметры Docker.\nШаги: построить конфигурацию с обоими источниками.\nОжидаемый результат: системное свойство имеет приоритет; выбран режим docker.")    void shouldPreferSystemPropertiesToEnvironment() {
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

        assertThat(configuration.mode()).as("Системное свойство должно иметь приоритет и выбирать docker mode").isEqualTo("docker");
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Проверка обязательного пути к JAR в local-режиме")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение local mode без AQA_SERVER_JAR")
    @Description("Предусловия: задан local-режим, но AQA_SERVER_JAR отсутствует.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_SERVER_JAR.")
    @Tag("negative")
    void shouldRequireJarInLocalMode() {
        assertThatThrownBy(() -> McpServerConfiguration.from(
                Map.of("AQA_SERVER_MODE", "local"),
                new Properties()
        )).as("Local mode без AQA_SERVER_JAR должен отклоняться с понятной ошибкой")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AQA_SERVER_JAR");
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Проверка обязательных параметров Docker-режима")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение Docker mode без AQA_DOCKER_ENV_FILE")
    @Description("Предусловия: задан Docker-режим и image, но отсутствует env-файл.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_DOCKER_ENV_FILE.")
    @Tag("negative")
    void shouldRequireImageAndEnvFileInDockerMode() {
        assertThatThrownBy(() -> McpServerConfiguration.from(
                Map.of(
                        "AQA_SERVER_MODE", "docker",
                        "AQA_DOCKER_IMAGE", "atlassian-mcp-light:test"
                ),
                new Properties()
        )).as("Docker mode без обязательного env-файла должен отклоняться")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AQA_DOCKER_ENV_FILE");
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Отклонение неизвестного режима запуска")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение неподдерживаемого AQA_SERVER_MODE=remote")
    @Description("Предусловия: AQA_SERVER_MODE задан как неподдерживаемое значение remote.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_SERVER_MODE.")
    @Tag("negative")
    void shouldRejectUnknownMode() {
        assertThatThrownBy(() -> McpServerConfiguration.from(
                Map.of("AQA_SERVER_MODE", "remote"),
                new Properties()
        )).as("Неизвестный режим запуска должен отклоняться")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AQA_SERVER_MODE");
    
    
    }
}
