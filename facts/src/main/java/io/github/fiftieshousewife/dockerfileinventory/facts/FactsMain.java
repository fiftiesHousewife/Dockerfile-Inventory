package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.io.IOException;
import java.nio.file.Path;

/** Runs Phase 1 from the environment: {@code DISCOVERY_DIR} (discovery's output directory) and {@code OUT_DIR}. */
public final class FactsMain {

    private FactsMain() {
    }

    public static void main(String[] args) throws IOException {
        System.out.println(new FactsRun(Path.of(required("DISCOVERY_DIR")), Path.of(required("OUT_DIR"))).run());
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set");
        }
        return value;
    }
}
