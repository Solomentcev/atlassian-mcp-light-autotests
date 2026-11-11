package ru.solomentsev.aqa.support;

import java.util.Locale;

/** Общие сведения об операционной системе для тестовой инфраструктуры. */
public final class OperatingSystem {

    private OperatingSystem() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
