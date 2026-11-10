package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The report job's entry point. It takes its settings from the environment, as the OpenShift job sets them:
 * {@code FACTS_DIR}, {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally {@code LINEAGE_DIR}, {@code CONVERTER_DIR},
 * {@code VALIDATOR_DIR}, {@code STALE_AFTER_MONTHS}, {@code LAYER_LIMIT} and {@code FOOTPRINTS}.
 */
public final class ReportMain {

    private static final Logger LOG = LoggerFactory.getLogger(ReportMain.class);

    private ReportMain() {
    }

    /** Writes the report under {@code OUT_DIR}; fails before reading anything if a required setting is missing. */
    public static void main(String[] args) {
        run(System.getenv());
    }

    /**
     * Writes the report with the given settings, as {@link #main} does with the environment's.
     *
     * @param env the settings
     */
    public static void run(Map<String, String> env) {
        String lineage = env.get("LINEAGE_DIR");
        String converter = env.get("CONVERTER_DIR");
        String validator = env.get("VALIDATOR_DIR");
        Path out = Path.of(required(env, "OUT_DIR"));
        new ReportRun(Path.of(required(env, "FACTS_DIR")), Path.of(required(env, "DISCOVERY_DIR")),
                lineage == null ? null : Path.of(lineage), converter == null ? null : Path.of(converter),
                validator == null ? null : Path.of(validator), catalogueSettings(env), out).run();
        LOG.info("report: report written to {}", out.resolve("index.html"));
    }

    static CatalogueSettings catalogueSettings(Map<String, String> env) {
        String footprints = env.get("FOOTPRINTS");
        return new CatalogueSettings(positive(env, "STALE_AFTER_MONTHS", CatalogueSettings.DEFAULTS.staleAfterMonths()),
                positive(env, "LAYER_LIMIT", CatalogueSettings.DEFAULTS.layerLimit()), footprints == null
                ? FootprintRules.defaults() : FootprintRules.read(Path.of(footprints)).then(FootprintRules.defaults()));
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
