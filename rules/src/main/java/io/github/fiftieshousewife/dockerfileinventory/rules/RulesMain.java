package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.nio.file.Path;
import java.util.Map;

/**
 * Runs the rules from the environment: {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally {@code LINEAGE_DIR}
 * (effective rules), {@code ALLOW_LIST} (S1), {@code EOL_LIST} (R2) and {@code CATALOGUE_DIR} (the rules over the
 * whole estate, from the catalogue stage's output). Detect-only: nothing is changed.
 */
public final class RulesMain {

    private RulesMain() {
    }

    public static void main(String[] args) {
        run(System.getenv());
    }

    /**
     * Runs the rules with the given settings, as {@link #main} does with the environment's.
     *
     * @param env the settings
     */
    public static void run(Map<String, String> env) {
        Path out = Path.of(required("OUT_DIR", env.get("OUT_DIR")));
        new RulesRun(Path.of(required("DISCOVERY_DIR", env.get("DISCOVERY_DIR"))), optional(env.get("LINEAGE_DIR")),
                optional(env.get("ALLOW_LIST")), optional(env.get("EOL_LIST")),
                optional(env.get("CATALOGUE_DIR")), out).run();
        System.out.println("Findings written to " + out.resolve("findings.jsonl"));
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
