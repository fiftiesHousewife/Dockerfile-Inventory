package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.nio.file.Path;

/**
 * Runs the rules from the environment: {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally {@code LINEAGE_DIR}
 * (effective rules), {@code ALLOW_LIST} (S1) and {@code EOL_LIST} (R2). Detect-only: nothing is changed.
 */
public final class RulesMain {

    private RulesMain() {
    }

    public static void main(String[] args) {
        Path out = Path.of(required("OUT_DIR", System.getenv("OUT_DIR")));
        new RulesRun(Path.of(required("DISCOVERY_DIR", System.getenv("DISCOVERY_DIR"))),
                optional(System.getenv("LINEAGE_DIR")), optional(System.getenv("ALLOW_LIST")),
                optional(System.getenv("EOL_LIST")), out).run();
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
