package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.nio.file.Path;
import java.util.Map;

/**
 * The validator job's entry point. It takes its settings from the environment: {@code FACTS_DIR},
 * {@code DISCOVERY_DIR}, {@code OUT_DIR}, and optionally {@code PROFILE} (a shipped profile's name or a file;
 * {@code openshift-4-restricted-v2} by default).
 */
public final class ValidatorMain {

    private ValidatorMain() {
    }

    /** Writes the action plan under {@code OUT_DIR}; fails before reading anything if a setting is missing or bad. */
    public static void main(String[] args) {
        Path out = run(System.getenv());
        System.out.println("Action plan written to " + out.resolve("action-plan.json"));
    }

    static Path run(Map<String, String> env) {
        Path out = Path.of(required(env, "OUT_DIR"));
        Profile profile = Profile.of(env.getOrDefault("PROFILE", Profile.DEFAULT));
        new ValidatorRun(Path.of(required(env, "FACTS_DIR")), Path.of(required(env, "DISCOVERY_DIR")), profile, out)
                .run();
        return out;
    }

    static String required(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value;
    }
}
