package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.nio.file.Path;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The validator job's entry point. It takes its settings from the environment: {@code FACTS_DIR},
 * {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally {@code PROFILE} (a shipped profile's name or a file;
 * {@code openshift-4-restricted-v2} by default), {@code LINEAGE_DIR} (the user each Dockerfile's registry images
 * run as), {@code CATALOGUE_DIR} (the catalogue stage's output: archetypes and what is built on what, for fixes) and
 * {@code RULES_DIR} (the rules stage's output, whose findings become actions).
 */
public final class ValidatorMain {

    private static final Logger LOG = LoggerFactory.getLogger(ValidatorMain.class);

    private ValidatorMain() {
    }

    /** Writes the action plan under {@code OUT_DIR}; fails before reading anything if a setting is missing or bad. */
    public static void main(String[] args) {
        Path out = run(System.getenv());
        LOG.info("validator: action plan written to {}", out.resolve("action-plan.json"));
    }

    /**
     * Writes the action plan with the given settings, as {@link #main} does with the environment's.
     *
     * @param env the settings
     * @return the output directory
     */
    public static Path run(Map<String, String> env) {
        Path out = Path.of(required(env, "OUT_DIR"));
        Profile profile = Profile.of(env.getOrDefault("PROFILE", Profile.DEFAULT));
        new ValidatorRun(Path.of(required(env, "FACTS_DIR")), Path.of(required(env, "DISCOVERY_DIR")),
                optional(env, "LINEAGE_DIR"), optional(env, "CATALOGUE_DIR"), optional(env, "RULES_DIR"), profile,
                out).run();
        return out;
    }

    static Path optional(Map<String, String> env, String name) {
        String value = env.get(name);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value;
    }
}
