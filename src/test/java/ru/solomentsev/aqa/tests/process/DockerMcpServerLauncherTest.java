package ru.solomentsev.aqa.process;


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

import java.nio.file.Path;
import java.util.List;

import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThat;
import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThatThrownBy;

@Epic("AQA Test Infrastructure")
    @Tag("unit")
    @Tag("docker")
@Tag("regression")
class DockerMcpServerLauncherTest {

    @Test
    @Feature("Docker Launcher")
    @Story("Формирование Docker run-команды для STDIO без TTY и опубликованных портов")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("docker run: image=atlassian-mcp-light:latest, stdin=-i, --rm; без -t и -p")
    @Description("Предусловия: Заданы Docker executable, run id, env-файл и image.\nШаги: сформировать docker run command.\nОжидаемый результат: команда содержит --rm, --name, label текущего прогона, -i и --env-file; не содержит TTY и публикацию портов.")
    @Tag("process")
    void shouldBuildInteractiveStdioRunCommandWithoutTtyOrPublishedPorts() {
        Allure.parameter("Docker executable", "docker");
        Allure.parameter("AQA_RUN_ID", "build_123");
        Allure.parameter("Docker env file", ".runtime.env");
        Allure.parameter("Docker image", "atlassian-mcp-light:latest");
        List<String> command = Allure.step("Сформировать команду запуска Docker для STDIO", () -> DockerMcpServerLauncher.buildRunCommand(
                "docker",
                "aml-build_123",
                Path.of(".runtime.env"),
                "atlassian-mcp-light:latest"
        ));

        assertThat(command).as("Docker run должен включать параметры интерактивного STDIO-запуска без TTY").containsSubsequence(
                "docker", "run", "--rm", "--name", "aml-build_123",
                "--label", "aqa.run-id=build_123", "-i", "--env-file"
        );
        int envFileOptionIndex = command.indexOf("--env-file");
        assertThat(envFileOptionIndex)
                .as("Docker run должен содержать опцию --env-file")
                .isGreaterThanOrEqualTo(0);
        assertThat(Path.of(command.get(envFileOptionIndex + 1)).toAbsolutePath().normalize())
                .as("Docker run должен передавать абсолютный путь к заданному файлу .runtime.env")
                .isEqualTo(Path.of(".runtime.env").toAbsolutePath().normalize());
        assertThat(command).as("Docker run должен использовать заданный image=atlassian-mcp-light:latest")
                .contains("atlassian-mcp-light:latest");
        assertThat(command).as("STDIO-контейнер нельзя запускать с TTY или опубликованными портами").doesNotContain("-t", "-p", "--publish");
    
    
    }

    @Test
    @Feature("Docker Launcher")
    @Story("Отклонение небезопасного Docker run id")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Docker launcher отклоняет AQA_RUN_ID=unsafe/run-id")
    @Description("Предусловия: Передан run id с недопустимым символом /.\nШаги: создать DockerMcpServerLauncher.\nОжидаемый результат: конструктор выбрасывает IllegalArgumentException с указанием runId.")
    @Tag("negative")
    void shouldRejectUnsafeRunId() {
        Allure.parameter("AQA_RUN_ID", "unsafe/run-id");
        Allure.parameter("Ожидаемая проверка", "runId должен содержать только безопасные символы");
        assertThatThrownBy(() -> Allure.step("Создать Docker launcher с AQA_RUN_ID=unsafe/run-id", () -> new DockerMcpServerLauncher(
                "docker",
                "atlassian-mcp-light:latest",
                Path.of(".runtime.env"),
                "unsafe/run-id",
                5
        ))).as("При AQA_RUN_ID=unsafe/run-id ожидается IllegalArgumentException с указанием runId")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
    
    
    }

    @Test
    @Feature("Docker Launcher")
    @Story("Отклонение нулевого timeout остановки контейнера")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Docker launcher отклоняет stopTimeoutSeconds=0")
    @Description("Предусловия: stop timeout задан равным нулю.\nШаги: создать DockerMcpServerLauncher.\nОжидаемый результат: конструктор выбрасывает IllegalArgumentException с указанием stopTimeoutSeconds.")
    @Tag("negative")
    void shouldRejectZeroStopTimeout() {
        Allure.parameter("Docker stop timeout, seconds", 0);
        Allure.parameter("Ожидаемая проверка", "stopTimeoutSeconds должен быть больше нуля");
        assertThatThrownBy(() -> Allure.step("Создать Docker launcher с stopTimeoutSeconds=0", () -> new DockerMcpServerLauncher(
                "docker",
                "atlassian-mcp-light:latest",
                Path.of(".runtime.env"),
                "run-123",
                0
        ))).as("При stopTimeoutSeconds=0 ожидается IllegalArgumentException с указанием stopTimeoutSeconds")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stopTimeoutSeconds");
    
    
    }
}