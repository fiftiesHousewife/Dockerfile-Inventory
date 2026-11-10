package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The converter's entry point. Settings come from the environment:
 * {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally {@code OPT_IN} (repositories that get generated classes)
 * and {@code ALLOW_LIST} (approved bases, for the generated TODOs).
 */
public final class ConverterMain {

    private static final Logger LOG = LoggerFactory.getLogger(ConverterMain.class);

    private ConverterMain() {
    }

    /** Converts with the environment's settings; arguments are ignored. */
    public static void main(String[] args) {
        run(System.getenv());
    }

    /**
     * Converts every Dockerfile of {@code DISCOVERY_DIR} into {@code OUT_DIR}.
     *
     * @param env the settings
     */
    public static void run(Map<String, String> env) {
        Path out = Path.of(required("OUT_DIR", env.get("OUT_DIR")));
        new ConverterRun(Path.of(required("DISCOVERY_DIR", env.get("DISCOVERY_DIR"))),
                optional(env.get("OPT_IN")), optional(env.get("ALLOW_LIST")), out,
                javaRoundTrip(env.get("JAVA_ROUND_TRIP"))).run();
        LOG.info("converter: conversions written to {}", out);
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
