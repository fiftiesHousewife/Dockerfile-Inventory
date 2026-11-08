package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Path;

/**
 * The converter's job entry point. Settings come from the environment, as the pipeline runs it as a pod:
 * {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally {@code OPT_IN} (repositories that get generated classes)
 * and {@code ALLOW_LIST} (approved bases, for the generated TODOs).
 */
public final class ConverterMain {

    private ConverterMain() {
    }

    /** Arguments are ignored: the job pod passes its settings in the environment. */
    public static void main(String[] args) {
        Path out = Path.of(required("OUT_DIR", System.getenv("OUT_DIR")));
        new ConverterRun(Path.of(required("DISCOVERY_DIR", System.getenv("DISCOVERY_DIR"))),
                optional(System.getenv("OPT_IN")), optional(System.getenv("ALLOW_LIST")), out,
                javaRoundTrip(System.getenv("JAVA_ROUND_TRIP"))).run();
        System.out.println("Conversions written to " + out);
    }

    static boolean javaRoundTrip(String value) {
        if (value == null || value.isBlank() || value.equals("true")) {
            return true;
        }
        if (value.equals("false")) {
            return false;
        }
        throw new IllegalStateException("JAVA_ROUND_TRIP is true or false, not " + value);
    }

    static Path optional(String value) {
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
