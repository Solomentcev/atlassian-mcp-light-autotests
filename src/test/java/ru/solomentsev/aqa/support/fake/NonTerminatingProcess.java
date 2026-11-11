package ru.solomentsev.aqa.support.fake;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/** Fake Process, который игнорирует destroyForcibly до явного изменения состояния тестом. */
public final class NonTerminatingProcess extends Process {

    private boolean destroyForciblyCalled;
    private boolean alive = true;

    public boolean wasDestroyForciblyCalled() {
        return destroyForciblyCalled;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

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
