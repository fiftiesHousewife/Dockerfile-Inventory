package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.util.Map;

/**
 * The report job's entry point. It takes its settings from the environment, as the OpenShift job sets them:
 * {@code FACTS_DIR}, {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally {@code LINEAGE_DIR}, {@code CONVERTER_DIR},
 * {@code STALE_AFTER_MONTHS}, {@code LAYER_LIMIT}, {@code FOOTPRINTS}, {@code LINK_THRESHOLD} and
 * {@code LINK_MARGIN}.
 */
public final class ReportMain {

    private ReportMain() {
    }

    /** Writes the report under {@code OUT_DIR}; fails before reading anything if a required setting is missing. */
    public static void main(String[] args) {
        Map<String, String> env = System.getenv();
        String lineage = env.get("LINEAGE_DIR");
        String converter = env.get("CONVERTER_DIR");
        Path out = Path.of(required(env, "OUT_DIR"));
        new ReportRun(Path.of(required(env, "FACTS_DIR")), Path.of(required(env, "DISCOVERY_DIR")),
                lineage == null ? null : Path.of(lineage), converter == null ? null : Path.of(converter),
                catalogueSettings(env), out).run();
        System.out.println("Report written to " + out.resolve("index.html"));
    }

    static CatalogueSettings catalogueSettings(Map<String, String> env) {
        String footprints = env.get("FOOTPRINTS");
        return new CatalogueSettings(positive(env, "STALE_AFTER_MONTHS", CatalogueSettings.DEFAULTS.staleAfterMonths()),
                positive(env, "LAYER_LIMIT", CatalogueSettings.DEFAULTS.layerLimit()), footprints == null
                ? FootprintRules.defaults() : FootprintRules.read(Path.of(footprints)).then(FootprintRules.defaults()),
                new LinkRule(share(env, "LINK_THRESHOLD", LinkRule.DEFAULT.threshold()),
                        share(env, "LINK_MARGIN", LinkRule.DEFAULT.margin())));
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

    static double share(Map<String, String> env, String name, double fallback) {
        String value = env.get(name);
        if (value == null) {
            return fallback;
        }
        if (!value.matches("0(\\.\\d+)?|1(\\.0+)?|\\.\\d+")) {
            throw new IllegalStateException(name + " must be a number from 0 to 1, not " + value);
        }
        return Double.parseDouble(value);
    }

    static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
