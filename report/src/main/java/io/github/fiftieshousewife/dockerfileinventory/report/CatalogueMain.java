package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.util.Map;

/**
 * The catalogue job's entry point: the report's catalogue, archetypes and passes without its pages, so the validator
 * can read them before the report runs. Its settings are the report's: {@code FACTS_DIR}, {@code DISCOVERY_DIR},
 * {@code OUT_DIR}, and optionally {@code LINEAGE_DIR}, {@code CONVERTER_DIR}, {@code STALE_AFTER_MONTHS},
 * {@code LAYER_LIMIT} and {@code FOOTPRINTS}.
 */
public final class CatalogueMain {

    private CatalogueMain() {
    }

    /** Writes the catalogue under {@code OUT_DIR}; fails before reading anything if a required setting is missing. */
    public static void main(String[] args) {
        Path out = run(System.getenv());
        System.out.println("Catalogue written to " + out.resolve(CatalogueRun.PATH));
    }

    static Path run(Map<String, String> env) {
        Path facts = Path.of(ReportMain.required(env, "FACTS_DIR"));
        Path discovery = Path.of(ReportMain.required(env, "DISCOVERY_DIR"));
        Path out = Path.of(ReportMain.required(env, "OUT_DIR"));
        String lineage = env.get("LINEAGE_DIR");
        String converter = env.get("CONVERTER_DIR");
        new ReportRun(facts, discovery, lineage == null ? null : Path.of(lineage),
                converter == null ? null : Path.of(converter), null, ReportMain.catalogueSettings(env), out)
                .catalogue();
        return out;
    }
}
