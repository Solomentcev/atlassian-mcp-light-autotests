package ru.solomentsev.aqa.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Читает простой dotenv-файл в формате KEY=VALUE. */
final class EnvFileLoader {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private EnvFileLoader() {
    }

    static Map<String, String> load(Path file) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        if (file == null || Files.notExists(file)) {
            return values;
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(".env path must point to a regular file");
        }

        int lineNumber = 0;
        for (String sourceLine : Files.readAllLines(file)) {
            lineNumber++;
            String line = sourceLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }

            int separator = line.indexOf('=');
            if (separator < 1) {
                throw invalidLine(lineNumber);
            }

            String key = line.substring(0, separator).trim();
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw invalidLine(lineNumber);
            }

            String value = parseValue(line.substring(separator + 1).trim(), lineNumber);
            values.put(key, value);
        }
        return values;
    }

    private static String parseValue(String source, int lineNumber) {
        if (source.isEmpty()) {
            return "";
        }

        char first = source.charAt(0);
        if (first == 39 || first == '"') {
            int closingQuote = source.indexOf(first, 1);
            if (closingQuote < 0 || (!source.substring(closingQuote + 1).trim().isEmpty()
                    && !source.substring(closingQuote + 1).trim().startsWith("#"))) {
                throw invalidLine(lineNumber);
            }
            return source.substring(1, closingQuote);
        }

        int commentStart = -1;
        for (int i = 1; i < source.length(); i++) {
            if (source.charAt(i) == '#' && Character.isWhitespace(source.charAt(i - 1))) {
                commentStart = i;
                break;
            }
        }
        return (commentStart >= 0 ? source.substring(0, commentStart) : source).trim();
    }

    private static IllegalArgumentException invalidLine(int lineNumber) {
        return new IllegalArgumentException("Invalid .env syntax at line " + lineNumber);
    }
}
