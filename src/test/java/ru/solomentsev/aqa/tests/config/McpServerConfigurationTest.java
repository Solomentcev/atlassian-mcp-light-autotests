package ru.solomentsev.aqa.tests.config;

import ru.solomentsev.aqa.config.McpServerConfiguration;
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
    @DisplayName("AQA_SERVER_MODE=local и AQA_SERVER_JAR=server/target/server.jar из environment выбирают local mode")
    @Description("Предусловия: Переданы AQA_SERVER_MODE=local и AQA_SERVER_JAR.\nШаги: построить конфигурацию из переменных окружения.\nОжидаемый результат: конфигурация выбирает режим local.")    void shouldReadLocalModeFromEnvironment() {
        Allure.parameter("AQA_SERVER_MODE", "local");
        Allure.parameter("AQA_SERVER_JAR", "server/target/server.jar");
        McpServerConfiguration configuration = Allure.step("Построить конфигурацию: AQA_SERVER_MODE=local, AQA_SERVER_JAR задан", () -> McpServerConfiguration.from(
                Map.of(
                        "AQA_SERVER_MODE", "local",
                        "AQA_SERVER_JAR", "server/target/server.jar"
                ),
                new Properties()
        ));

        assertThat(configuration.mode()).as("При AQA_SERVER_MODE=local конфигурация должна выбрать local mode").isEqualTo("local");
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Приоритет системных свойств над переменными окружения")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("System property aqa.server.mode=docker переопределяет environment AQA_SERVER_MODE=local [image=atlassian-mcp-light:test]")
    @Description("Предусловия: Environment задаёт local, system properties задают docker и параметры Docker.\nШаги: построить конфигурацию с обоими источниками.\nОжидаемый результат: системное свойство имеет приоритет; выбран режим docker.")    void shouldPreferSystemPropertiesToEnvironment() {
        Allure.parameter("Environment AQA_SERVER_MODE", "local");
        Allure.parameter("System property aqa.server.mode", "docker");
        Allure.parameter("Docker image", "atlassian-mcp-light:test");
        Allure.parameter("Docker env file", ".runtime.env");
        Properties properties = new Properties();
        properties.setProperty("aqa.server.mode", "docker");
        properties.setProperty("aqa.docker.image", "atlassian-mcp-light:test");
        properties.setProperty("aqa.docker.envFile", ".runtime.env");

        McpServerConfiguration configuration = Allure.step("Построить конфигурацию: environment=local, system property= docker", () -> McpServerConfiguration.from(
                Map.of(
                        "AQA_SERVER_MODE", "local",
                        "AQA_SERVER_JAR", "server.jar"
                ),
                properties
        ));

        assertThat(configuration.mode()).as("Системное свойство должно иметь приоритет и выбирать docker mode").isEqualTo("docker");
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Проверка обязательного пути к JAR в local-режиме")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("AQA_SERVER_MODE=local без AQA_SERVER_JAR отклоняется")
    @Description("Предусловия: задан local-режим, но AQA_SERVER_JAR отсутствует.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_SERVER_JAR.")
    @Tag("negative")
    void shouldRequireJarInLocalMode() {
        Allure.parameter("AQA_SERVER_MODE", "local");
        Allure.parameter("AQA_SERVER_JAR", "отсутствует");
        assertThatThrownBy(() -> Allure.step("Построить конфигурацию: AQA_SERVER_MODE=local, AQA_SERVER_JAR отсутствует", () -> McpServerConfiguration.from(
                Map.of("AQA_SERVER_MODE", "local"),
                new Properties()
        ))).as("При AQA_SERVER_MODE=local без AQA_SERVER_JAR ожидается IllegalArgumentException с именем AQA_SERVER_JAR")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AQA_SERVER_JAR");
    
    
    }

    @Test
    @Feature("Server Configuration")
    @Story("Проверка обязательных параметров Docker-режима")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Docker mode с image=atlassian-mcp-light:test без AQA_DOCKER_ENV_FILE отклоняется")
    @Description("Предусловия: задан Docker-режим и image, но отсутствует env-файл.\nШаги: построить конфигурацию.\nОжидаемый результат: выбрасывается IllegalArgumentException с указанием AQA_DOCKER_ENV_FILE.")
    @Tag("negative")
    void shouldRequireImageAndEnvFileInDockerMode() {
        Allure.parameter("AQA_SERVER_MODE", "docker");
        Allure.parameter("AQA_DOCKER_IMAGE", "atlassian-mcp-light:test");
        Allure.parameter("AQA_DOCKER_ENV_FILE", "отсутствует");
        assertThatThrownBy(() -> Allure.step("Построить конфигурацию: docker image задан, AQA_DOCKER_ENV_FILE отсутствует", () -> McpServerConfiguration.from(
                Map.of(
                        "AQA_SERVER_MODE", "docker",
                        "AQA_DOCKER_IMAGE", "atlassian-mcp-light:test"
                ),
                new Properties()
        ))).as("При AQA_SERVER_MODE=docker без AQA_DOCKER_ENV_FILE ожидается IllegalArgumentException")
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
        Allure.parameter("AQA_SERVER_MODE", "remote");
        assertThatThrownBy(() -> Allure.step("Построить конфигурацию с неподдерживаемым AQA_SERVER_MODE=remote", () -> McpServerConfiguration.from(
                Map.of("AQA_SERVER_MODE", "remote"),
                new Properties()
        ))).as("При AQA_SERVER_MODE=remote ожидается IllegalArgumentException с именем AQA_SERVER_MODE")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AQA_SERVER_MODE");
    
    
    }
}
