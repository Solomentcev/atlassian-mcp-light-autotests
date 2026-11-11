package ru.solomentsev.aqa.tests.process;

import ru.solomentsev.aqa.process.ManagedProcess;
import ru.solomentsev.aqa.process.ProcessBuilderLauncher;
import ru.solomentsev.aqa.process.StderrCollector;
import ru.solomentsev.aqa.testdata.McpTestData;
import ru.solomentsev.aqa.support.OperatingSystem;

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
import java.util.Map;

import static ru.solomentsev.aqa.support.assertions.AllureAssertions.assertThat;

@Epic("AQA Test Infrastructure")
@Tag("unit")
@Tag("process")
@Tag("regression")
class ProcessBuilderLauncherTest {

    @Test
    @Feature("Process Builder Launcher")
    @Story("Запуск процесса и отдельный сбор STDERR")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("ProcessBuilder запускает java -version [timeout=15000 ms], exitCode=0; STDERR собирается отдельно")
    @Description("Предусловия: доступен исполняемый файл java из текущего java.home.\nШаги: запустить команду java -version через ProcessBuilderLauncher; ожидать завершения не более 15000 ms; дождаться чтения STDERR.\nОжидаемый результат: процесс завершается в заданный timeout с exit code=0; STDERR collector завершается и содержит строку версии Java; поток ошибок читается отдельно от STDOUT.")    void shouldLaunchProcessAndCaptureStderrSeparately() throws Exception {
        Allure.parameter("Команда", "java -version");
        Allure.parameter("Process timeout", McpTestData.getJavaVersionProcessTimeout().toMillis() + " ms");
        Allure.parameter("STDERR collection timeout", McpTestData.getStderrCollectionTimeout().toMillis() + " ms");
        String executable = Path.of(
                System.getProperty("java.home"),
                "bin",
                OperatingSystem.isWindows() ? "java.exe" : "java"
        ).toString();

        ManagedProcess process = Allure.step("Запустить java -version через ProcessBuilder", () -> new ProcessBuilderLauncher().launch(
                List.of(executable, "-version"),
                Map.of(),
                null
        ));
        StderrCollector stderrCollector = new StderrCollector(process.stderr(), McpTestData.getStderrBufferSizeBytes());

        try {
            assertThat(Allure.step("Дождаться завершения java -version", () -> process.waitFor(McpTestData.getJavaVersionProcessTimeout())))
                    .as("Процесс java -version должен завершиться в пределах 15 секунд")
                    .isTrue();
            assertThat(process.exitCode()).as("После ожидания не более %d ms процесс java -version должен завершиться с exit code=0, фактически: %d", McpTestData.getJavaVersionProcessTimeout().toMillis(), process.exitCode()).isZero();
            assertThat(Allure.step("Дождаться сбора STDERR", () -> stderrCollector.await(McpTestData.getStderrCollectionTimeout())))
                    .as("STDERR collector должен завершить чтение потока")
                    .isTrue();
            assertThat(stderrCollector.content()).as("После java -version в отдельном STDERR ожидается текст версии Java; фактический вывод: %s", stderrCollector.content()).contains("version");
        } finally {
            Allure.step("Закрыть запущенный процесс", process::close);
            Allure.step("Закрыть STDERR collector", stderrCollector::close);
        }
    
    
    }

}