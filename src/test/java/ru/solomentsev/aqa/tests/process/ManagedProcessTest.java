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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("AQA Test Infrastructure")
    @Tag("unit")
    @Tag("process")
class ManagedProcessTest {

    @Test
    @Feature("Managed Process")
    @Story("Диагностика процесса, не завершившегося после принудительной остановки")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Диагностика процесса, не завершившегося после принудительной остановки")
    @Description("Предусловия: Используется имитация процесса, который остаётся жив после destroyForcibly.\nШаги: вызвать close; проверить результат принудительной остановки; затем смоделировать завершение процесса и повторить close.\nОжидаемый результат: первая попытка сообщает IOException о незавершённом процессе; после фактического завершения повторный close проходит успешно.")
    @Tag("process")
    void shouldReportFailureWhenProcessSurvivesForcedTermination() throws IOException {
        Allure.parameter("Состояние процесса после destroyForcibly", "остаётся запущенным");
        Allure.parameter("Повторное состояние процесса", "завершён");
        NonTerminatingProcess process = new NonTerminatingProcess();
        ManagedProcess managedProcess = new ManagedProcess(process);

        assertThatThrownBy(() -> Allure.step("Закрыть процесс, который остаётся активным после destroyForcibly", managedProcess::close))
                .as("Если процесс остаётся активным после destroyForcibly, close должен выбросить IOException")
                .isInstanceOf(IOException.class)
                .hasMessageContaining("did not terminate after destroyForcibly");
        assertThat(process.destroyForciblyCalled).as("После неудачного graceful shutdown ManagedProcess должен вызвать destroyForcibly, фактически: %s", process.destroyForciblyCalled).isTrue();

        process.alive = false;
        Allure.step("Повторно закрыть ManagedProcess после фактического завершения дочернего процесса", managedProcess::close);
        assertThat(managedProcess.isAlive()).as("После завершения процесса ManagedProcess должен сообщать isAlive=false").isFalse();
    
    
    }

    private static final class NonTerminatingProcess extends Process {

        private boolean destroyForciblyCalled;
        private boolean alive = true;

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return !alive;
        }

        @Override
        public int exitValue() {
            throw new IllegalThreadStateException("still running");
        }

        @Override
        public void destroy() {
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCalled = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public long pid() {
            return 123L;
        }
    }
}