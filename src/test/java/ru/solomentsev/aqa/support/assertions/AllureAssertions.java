package ru.solomentsev.aqa.support.assertions;

import io.qameta.allure.Allure;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * AssertJ-совместимые проверки, каждая из которых отображается отдельным шагом Allure.
 *
 * <p>Описание проверки задаётся через {@link AllureAssert#as(String, Object...)}.
 * В заголовок шага добавляются безопасно отформатированные фактическое и ожидаемое значения.</p>
 */
public final class AllureAssertions {

    private AllureAssertions() {
    }

    public static <T> AllureAssert<T> assertThat(T actual) {
        return new AllureAssert<>(actual);
    }

    public static ThrowableAssertStep assertThatThrownBy(ThrowableAssert.ThrowingCallable callable) {
        return new ThrowableAssertStep(callable);
    }

    public static final class AllureAssert<T> {

        private final T actual;
        private String description = "Проверка AssertJ";

        private AllureAssert(T actual) {
            this.actual = actual;
        }

        public AllureAssert<T> as(String format, Object... args) {
            this.description = args == null || args.length == 0
                    ? format
                    : String.format(Locale.ROOT, format, args);
            return this;
        }

        public AllureAssert<T> isEqualTo(Object expected) {
            step("фактически=" + display(actual) + ", ожидается=" + display(expected),
                    () -> Assertions.assertThat(actual).isEqualTo(expected));
            return this;
        }

        public AllureAssert<T> isTrue() {
            step("фактически=" + display(actual) + ", ожидается=true",
                    () -> Assertions.assertThat((Boolean) actual).isTrue());
            return this;
        }

        public AllureAssert<T> isFalse() {
            step("фактически=" + display(actual) + ", ожидается=false",
                    () -> Assertions.assertThat((Boolean) actual).isFalse());
            return this;
        }

        public AllureAssert<T> isZero() {
            step("фактически=" + display(actual) + ", ожидается=0",
                    () -> Assertions.assertThat((Integer) actual).isZero());
            return this;
        }

        public AllureAssert<T> isNotNull() {
            step("фактически=" + display(actual) + ", ожидается не null",
                    () -> Assertions.assertThat(actual).isNotNull());
            return this;
        }

        public AllureAssert<T> isNull() {
            step("фактически=" + display(actual) + ", ожидается=null",
                    () -> Assertions.assertThat(actual).isNull());
            return this;
        }

        public AllureAssert<T> isNotBlank() {
            step("фактически=" + display(actual) + ", ожидается непустая строка",
                    () -> Assertions.assertThat((String) actual).isNotBlank());
            return this;
        }

        public AllureAssert<T> isInstanceOf(Class<?> expectedType) {
            step("фактически=" + typeOf(actual) + ", ожидается тип=" + expectedType.getSimpleName(),
                    () -> Assertions.assertThat(actual).isInstanceOf(expectedType));
            return this;
        }

        public AllureAssert<T> hasMessageContaining(String expectedPart) {
            step("фактически=" + throwableMessage(actual) + ", ожидается сообщение содержит=" + display(expectedPart),
                    () -> Assertions.assertThat((Throwable) actual).hasMessageContaining(expectedPart));
            return this;
        }

        public AllureAssert<T> contains(Object... expectedValues) {
            step("фактически=" + display(actual) + ", ожидается содержит=" + displayExpected(expectedValues),
                    () -> {
                        if (actual instanceof CharSequence) {
                            String[] values = Arrays.stream(expectedValues).map(String::valueOf).toArray(String[]::new);
                            Assertions.assertThat(actual.toString()).contains(values);
                        } else if (actual instanceof Iterable<?> iterable) {
                            Assertions.assertThat((Iterable<Object>) iterable).contains(expectedValues);
                        } else if (actual instanceof Object[] array) {
                            Assertions.assertThat(array).contains(expectedValues);
                        } else {
                            throw new AssertionError("Операция contains не поддерживается для " + typeOf(actual));
                        }
                    });
            return this;
        }

        public AllureAssert<T> doesNotContain(Object... unexpectedValues) {
            step("фактически=" + display(actual) + ", ожидается отсутствие указанных значений (значения скрыты)",
                    () -> {
                        if (actual instanceof CharSequence) {
                            String[] values = Arrays.stream(unexpectedValues).map(String::valueOf).toArray(String[]::new);
                            Assertions.assertThat(actual.toString()).doesNotContain(values);
                        } else if (actual instanceof Iterable<?> iterable) {
                            Assertions.assertThat((Iterable<Object>) iterable).doesNotContain(unexpectedValues);
                        } else if (actual instanceof Object[] array) {
                            Assertions.assertThat(array).doesNotContain(unexpectedValues);
                        } else {
                            throw new AssertionError("Операция doesNotContain не поддерживается для " + typeOf(actual));
                        }
                    });
            return this;
        }

        public AllureAssert<T> containsExactly(Object... expectedValues) {
            step("фактически=" + display(actual) + ", ожидается точная последовательность=" + displayExpected(expectedValues),
                    () -> Assertions.assertThat((Iterable<Object>) actual).containsExactly(expectedValues));
            return this;
        }

        public AllureAssert<T> containsExactlyInAnyOrderElementsOf(Iterable<?> expectedValues) {
            step("фактически=" + display(actual) + ", ожидается=" + display(expectedValues),
                    () -> Assertions.assertThat((Iterable<Object>) actual)
                            .containsExactlyInAnyOrderElementsOf((Iterable<Object>) expectedValues));
            return this;
        }

        public AllureAssert<T> containsSubsequence(Object... expectedValues) {
            step("фактически=" + display(actual) + ", ожидается последовательность=" + displayExpected(expectedValues),
                    () -> Assertions.assertThat((Iterable<Object>) actual).containsSubsequence(expectedValues));
            return this;
        }

        public AllureAssert<T> startsWith(String expectedPrefix) {
            step("фактически=" + display(actual) + ", ожидается начало=" + display(expectedPrefix),
                    () -> Assertions.assertThat(actual.toString()).startsWith(expectedPrefix));
            return this;
        }

        public AllureAssert<T> isGreaterThan(int expected) {
            step("фактически=" + display(actual) + ", ожидается больше " + expected,
                    () -> Assertions.assertThat((Integer) actual).isGreaterThan(expected));
            return this;
        }

        public AllureAssert<T> isGreaterThanOrEqualTo(int expected) {
            step("фактически=" + display(actual) + ", ожидается не меньше " + expected,
                    () -> Assertions.assertThat((Integer) actual).isGreaterThanOrEqualTo(expected));
            return this;
        }

        private void step(String values, Runnable assertion) {
            Allure.step(description + ": " + values, assertion::run);
        }
    }

    public static final class ThrowableAssertStep {

        private final Throwable actual;
        private String description = "Проверка выброшенного исключения";

        private ThrowableAssertStep(ThrowableAssert.ThrowingCallable callable) {
            Throwable thrown = null;
            try {
                callable.call();
            } catch (Throwable exception) {
                thrown = exception;
            }
            this.actual = thrown;
        }

        public ThrowableAssertStep as(String format, Object... args) {
            this.description = args == null || args.length == 0
                    ? format
                    : String.format(Locale.ROOT, format, args);
            return this;
        }

        public ThrowableAssertStep isInstanceOf(Class<?> expectedType) {
            Allure.step(description + ": фактически=" + typeOf(actual)
                    + ", ожидается тип=" + expectedType.getSimpleName(),
                    () -> Assertions.assertThat(actual).isInstanceOf(expectedType));
            return this;
        }

        public ThrowableAssertStep hasMessageContaining(String expectedPart) {
            Allure.step(description + ": фактически=" + throwableMessage(actual)
                    + ", ожидается сообщение содержит=" + display(expectedPart),
                    () -> Assertions.assertThat(actual).hasMessageContaining(expectedPart));
            return this;
        }
    }

    private static String display(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof Path) {
            return String.valueOf(value);
        }
        if (value instanceof CharSequence text) {
            String safe = text.toString()
                    .replaceAll("(?i)(token|password|secret)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2[REDACTED]");
            return safe.length() > 160 ? safe.substring(0, 157) + "..." : safe;
        }
        if (value instanceof Collection<?> collection) {
            String values = collection.stream()
                    .limit(12)
                    .map(AllureAssertions::display)
                    .toList()
                    .toString();
            return collection.size() > 12
                    ? values.substring(0, values.length() - 1) + ", ...] (всего " + collection.size() + ")"
                    : values;
        }
        if (value instanceof Map<?, ?> map) {
            return "записей=" + map.size();
        }
        if (value instanceof Iterable<?> iterable) {
            java.util.List<String> values = new java.util.ArrayList<>();
            for (Object item : iterable) {
                if (values.size() == 12) {
                    break;
                }
                values.add(display(item));
            }
            return values.toString();
        }
        return typeOf(value);
    }

    private static String displayExpected(Object[] values) {
        return Arrays.stream(values).map(AllureAssertions::display).toList().toString();
    }

    private static String typeOf(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static String throwableMessage(Object value) {
        return value instanceof Throwable throwable
                ? display(throwable.getMessage())
                : "исключение не выброшено";
    }
}
