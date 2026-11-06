package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;

/**
 * Runs the report from the environment: {@code FACTS_DIR}, {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally
 * {@code LINEAGE_DIR} (effective users from Phase 1b) and {@code EOL_LIST} (the internal end-of-life CSV).
 */
public final class ReportMain {

    private ReportMain() {
    }

    public static void main(String[] args) {
        String lineage = System.getenv("LINEAGE_DIR");
        String eolList = System.getenv("EOL_LIST");
        Path out = Path.of(required("OUT_DIR"));
        new ReportRun(Path.of(required("FACTS_DIR")), Path.of(required("DISCOVERY_DIR")),
                lineage == null ? null : Path.of(lineage),
                eolList == null ? EolList.empty() : EolList.read(Path.of(eolList)), out).run();
        System.out.println("Report written to " + out.resolve("dashboard.html"));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
