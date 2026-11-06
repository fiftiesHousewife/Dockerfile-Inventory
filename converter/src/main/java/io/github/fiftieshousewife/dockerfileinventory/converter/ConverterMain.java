package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Path;

/**
 * Runs the converter from the environment: {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally {@code OPT_IN}
 * (repositories that get generated classes, one {@code owner/repo} per line) and {@code ALLOW_LIST} (approved
 * bases, for the TODOs).
 */
public final class ConverterMain {

    private ConverterMain() {
    }

    public static void main(String[] args) {
        Path out = Path.of(required("OUT_DIR"));
        new ConverterRun(Path.of(required("DISCOVERY_DIR")), optional("OPT_IN"), optional("ALLOW_LIST"), out).run();
        System.out.println("Conversions written to " + out);
    }

    private static Path optional(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
