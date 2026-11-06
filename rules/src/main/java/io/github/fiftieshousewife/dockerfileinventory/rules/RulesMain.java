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
        Path out = Path.of(required("OUT_DIR"));
        new RulesRun(Path.of(required("DISCOVERY_DIR")), optional("LINEAGE_DIR"), optional("ALLOW_LIST"),
                optional("EOL_LIST"), out).run();
        System.out.println("Findings written to " + out.resolve("findings.jsonl"));
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
