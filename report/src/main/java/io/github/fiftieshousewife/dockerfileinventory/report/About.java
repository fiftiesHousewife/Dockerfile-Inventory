package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Map;

final class About {

    private About() {
    }

    static final Map<String, String> TEXT = Map.of("Archetypes",
            "An archetype is a family of Dockerfiles built by the same recipe (the same base, nearly the same "
            + "packages, user and ports) even when their text differs, whatever program each one starts. Few "
            + "archetypes covering many Dockerfiles means a few maintained templates could replace them, with the "
            + "program as a parameter.");
}
