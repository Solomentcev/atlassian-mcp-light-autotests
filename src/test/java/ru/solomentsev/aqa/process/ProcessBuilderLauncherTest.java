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
        Allure.step("Запуск процесса и отдельный сбор STDERR", () -> {
            String executable = Path.of(
                    System.getProperty("java.home"),
                    "bin",
                    isWindows() ? "java.exe" : "java"
            ).toString();

            ManagedProcess process = new ProcessBuilderLauncher().launch(
                    List.of(executable, "-version"),
                    Map.of(),
                    null
            );
            StderrCollector stderrCollector = new StderrCollector(process.stderr(), 8_192);

            try {
                assertThat(process.waitFor(Duration.ofSeconds(15))).isTrue();
                assertThat(process.exitCode()).isZero();
                assertThat(stderrCollector.await(Duration.ofSeconds(3))).isTrue();
                assertThat(stderrCollector.content()).contains("version");
            } finally {
                process.close();
                stderrCollector.close();
            }
        
        });
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}