package ru.solomentsev.aqa.tests.process;

import ru.solomentsev.aqa.process.ManagedProcess;
import ru.solomentsev.aqa.process.ProcessBuilderLauncher;
import ru.solomentsev.aqa.process.StderrCollector;
import ru.solomentsev.aqa.testdata.McpTestData;

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
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("AQA Test Infrastructure")
    @Tag("unit")
    @Tag("process")
class ProcessBuilderLauncherTest {

    @Test
    @Feature("Process Builder Launcher")
    @Story("Запуск процесса и отдельный сбор STDERR")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Запуск процесса и отдельный сбор STDERR")
    @Description("Предусловия: Доступен исполняемый файл Java текущей JVM.\nШаги: запустить java -version через ProcessBuilderLauncher; дождаться завершения; прочитать STDERR collector.\nОжидаемый результат: процесс завершается с кодом 0; диагностический вывод доступен через STDERR отдельно от STDOUT.")
    @Tag("process")
    void shouldLaunchProcessAndCaptureStderrSeparately() throws Exception {
        String executable = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java"
        ).toString();

        ManagedProcess process = Allure.step("Запустить java -version через ProcessBuilder", () -> new ProcessBuilderLauncher().launch(
                List.of(executable, "-version"),
                Map.of(),
                null
        ));
        StderrCollector stderrCollector = new StderrCollector(process.stderr(), 8_192);

        try {
            assertThat(Allure.step("Дождаться завершения java -version", () -> process.waitFor(McpTestData.JAVA_VERSION_PROCESS_TIMEOUT)))
                    .as("Процесс java -version должен завершиться в пределах 15 секунд")
                    .isTrue();
            assertThat(process.exitCode()).as("Процесс java -version должен завершиться с кодом 0").isZero();
            assertThat(Allure.step("Дождаться сбора STDERR", () -> stderrCollector.await(McpTestData.STDERR_COLLECTION_TIMEOUT)))
                    .as("STDERR collector должен завершить чтение потока")
                    .isTrue();
            assertThat(stderrCollector.content()).as("STDERR должен содержать сведения о версии Java").contains("version");
        } finally {
            Allure.step("Закрыть запущенный процесс", process::close);
            Allure.step("Закрыть STDERR collector", stderrCollector::close);
        }
    
    
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}