package ru.solomentsev.aqa.tests.process;

import ru.solomentsev.aqa.process.DockerMcpServerLauncher;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("AQA Test Infrastructure")
    @Tag("unit")
    @Tag("docker")
class DockerMcpServerLauncherTest {

    @Test
    @Feature("Docker Launcher")
    @Story("Формирование Docker run-команды для STDIO без TTY и опубликованных портов")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Формирование Docker run-команды для STDIO без TTY и опубликованных портов")
    @Description("Предусловия: Заданы Docker executable, run id, env-файл и image.\nШаги: сформировать docker run command.\nОжидаемый результат: команда содержит --rm, --name, label текущего прогона, -i и --env-file; не содержит TTY и публикацию портов.")
    @Tag("process")
    void shouldBuildInteractiveStdioRunCommandWithoutTtyOrPublishedPorts() {        List<String> command = Allure.step("Сформировать команду запуска Docker для STDIO", () -> DockerMcpServerLauncher.buildRunCommand(
                "docker",
                "aml-build_123",
                Path.of(".runtime.env"),
                "atlassian-mcp-light:latest"
        ));

        assertThat(command).as("Docker run должен включать параметры интерактивного STDIO-запуска без TTY").containsSubsequence(
                "docker", "run", "--rm", "--name", "aml-build_123",
                "--label", "aqa.run-id=build_123", "-i", "--env-file"
        );
        assertThat(command).as("Docker run должен использовать заданный image").contains("atlassian-mcp-light:latest");
        assertThat(command).as("STDIO-контейнер нельзя запускать с TTY или опубликованными портами").doesNotContain("-t", "-p", "--publish");
    
    
    }

    @Test
    @Feature("Docker Launcher")
    @Story("Отклонение небезопасного Docker run id")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение небезопасного Docker run id")
    @Description("Предусловия: Передан run id с недопустимым символом /.\nШаги: создать DockerMcpServerLauncher.\nОжидаемый результат: конструктор выбрасывает IllegalArgumentException с указанием runId.")
    @Tag("negative")
    void shouldRejectUnsafeRunId() {        assertThatThrownBy(() -> new DockerMcpServerLauncher(
                "docker",
                "atlassian-mcp-light:latest",
                Path.of(".runtime.env"),
                "unsafe/run-id",
                5
        )).as("Docker launcher должен отклонять небезопасный runId")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
    
    
    }

    @Test
    @Feature("Docker Launcher")
    @Story("Отклонение нулевого timeout остановки контейнера")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Отклонение нулевого timeout остановки контейнера")
    @Description("Предусловия: stop timeout задан равным нулю.\nШаги: создать DockerMcpServerLauncher.\nОжидаемый результат: конструктор выбрасывает IllegalArgumentException с указанием stopTimeoutSeconds.")
    @Tag("negative")
    void shouldRejectZeroStopTimeout() {        assertThatThrownBy(() -> new DockerMcpServerLauncher(
                "docker",
                "atlassian-mcp-light:latest",
                Path.of(".runtime.env"),
                "run-123",
                0
        )).as("Docker launcher должен отклонять нулевой stop timeout")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stopTimeoutSeconds");
    
    
    }
}