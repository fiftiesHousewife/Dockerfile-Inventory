package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.util.Map;

/**
 * The report job's entry point. It takes its settings from the environment, as the OpenShift job sets them:
 * {@code FACTS_DIR}, {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally {@code LINEAGE_DIR}, {@code EOL_LIST},
 * {@code STALE_AFTER_MONTHS} and {@code LAYER_LIMIT}.
 */
public final class ReportMain {

    private ReportMain() {
    }

    /** Writes the report under {@code OUT_DIR}; fails before reading anything if a required setting is missing. */
    public static void main(String[] args) {
        Map<String, String> env = System.getenv();
        String lineage = env.get("LINEAGE_DIR");
        String eolList = env.get("EOL_LIST");
        Path out = Path.of(required(env, "OUT_DIR"));
        new ReportRun(Path.of(required(env, "FACTS_DIR")), Path.of(required(env, "DISCOVERY_DIR")),
                lineage == null ? null : Path.of(lineage),
                eolList == null ? EolList.empty() : EolList.read(Path.of(eolList)), catalogueSettings(env), out).run();
        System.out.println("Report written to " + out.resolve("index.html"));
    }

    static CatalogueSettings catalogueSettings(Map<String, String> env) {
        return new CatalogueSettings(positive(env, "STALE_AFTER_MONTHS", CatalogueSettings.DEFAULTS.staleAfterMonths()),
                positive(env, "LAYER_LIMIT", CatalogueSettings.DEFAULTS.layerLimit()));
    }

    static int positive(Map<String, String> env, String name, int fallback) {
        String value = env.get(name);
        if (value == null) {
            return fallback;
        }
        if (!value.matches("[1-9][0-9]{0,8}")) {
            throw new IllegalStateException(name + " must be a positive whole number, not " + value);
        }
        return Integer.parseInt(value);
    }

    static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
