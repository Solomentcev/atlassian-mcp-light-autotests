package ru.solomentsev.aqa.process;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedProcessTest {

    @Test
    void shouldReportFailureWhenProcessSurvivesForcedTermination() throws IOException {
        NonTerminatingProcess process = new NonTerminatingProcess();
        ManagedProcess managedProcess = new ManagedProcess(process);

        assertThatThrownBy(managedProcess::close)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("did not terminate after destroyForcibly");
        assertThat(process.destroyForciblyCalled).isTrue();

        process.alive = false;
        managedProcess.close();
        assertThat(managedProcess.isAlive()).isFalse();
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