package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchetypeCardsTest {

    @Test
    void aCardHasItsSentenceSizeRepositoriesAndTheRepresentativesLocationAndText(@TempDir Path discovery)
            throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/b1"), "FROM ubi9/ubi-minimal:9.4\nUSER 1001\n");
        String signature = "ubi-minimal:9|||NUMERIC||NONE|stages=1";
        Dockerfile file = new Dockerfile("b1", "t", "s", signature, new Base("ubi9/ubi-minimal", "9.4", null),
                Set.of(), Set.of(), "NUMERIC", 0, 0,
                List.of(new Location("org/app", "Dockerfile", "RELEASED"),
                        new Location("org/api", "Dockerfile", "RELEASED")), 0);
        Estate estate = new Estate(List.of(file), List.of(), List.of(), Map.of());
        List<Archetype> archetypes = IntStream.range(0, 11).mapToObj(i -> new Archetype(i + 1, "k" + i, 2,
                List.of("b1", "b2"), signature, i == 0 ? "b1" : "gone", List.of("org/api", "org/app"))).toList();

        List<ArchetypeCards.Card> cards = ArchetypeCards.of(new Ranking(archetypes, 22, 1, 1), estate, discovery);

        assertThat(cards).satisfies(all -> {
            assertThat(all).hasSize(10);
            assertThat(all.getFirst()).isEqualTo(new ArchetypeCards.Card(1,
                    "Builds on ubi9/ubi-minimal:9.4, installs no packages, runs as a numeric user, exposes no ports, "
                            + "sets no start command of its own, in one stage.",
                    2, 2, List.of("org/api", "org/app"), "org/app: Dockerfile",
                    "FROM ubi9/ubi-minimal:9.4\nUSER 1001\n"));
            assertThat(all.get(1).exampleText()).isNull();
        });
    }

    @Test
    void aTemplatesCardTakesItsExampleFromTheFailuresAndItsSentenceFromItsGroup(@TempDir Path discovery)
            throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/t1"), "FROM {{ spec.base }}\n");
        Estate estate = new Estate(List.of(), List.of(new Failure("t1", "a template, not a Dockerfile (x)",
                List.of(new Location("org/tpl", "src/Dockerfile", "RELEASED")))), List.of(), Map.of());
        Archetype templates = new Archetype(1, "templates", 1, List.of("t1"), "templates", "t1", List.of("org/tpl"));

        assertThat(ArchetypeCards.of(new Ranking(List.of(templates), 1, 1, 1), estate, discovery).getFirst())
                .isEqualTo(new ArchetypeCards.Card(1,
                        "Templates saved under a Dockerfile's name (Jinja and similar), not Dockerfiles.", 1, 1,
                        List.of("org/tpl"), "org/tpl: src/Dockerfile", "FROM {{ spec.base }}\n"));
    }

    @Test
    void anExamplesSecretValuesAreHiddenBeforeItIsShown(@TempDir Path discovery) throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/s1"), "FROM ubi9\nENV DB_PASSWORD=hunter2\n");

        assertThat(ArchetypeCards.exampleText(discovery.resolve("blobs/s1")))
                .isEqualTo("FROM ubi9\nENV DB_PASSWORD=(hidden)\n");
    }

    @Test
    void aMissingBlobHasNoExampleText(@TempDir Path discovery) {
        assertThat(ArchetypeCards.exampleText(discovery.resolve("blobs/none"))).isNull();
    }

    @Test
    void aCardWithoutALocationHasNoExampleEvenWhenTheBlobExists(@TempDir Path discovery) throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/b1"), "FROM ubi9\n");
        Archetype archetype = new Archetype(3, "k", 1, List.of("b1"), "parse-failures", "b1", List.of());

        assertThat(ArchetypeCards.card(archetype, null, List.of(), discovery)).isEqualTo(new ArchetypeCards.Card(3,
                "Dockerfiles the parser could not read.", 1, 1, List.of(), null, null));
    }

    @Test
    void theBaseIsNamedWithItsTagWhenItHasOneAndUnknownWithoutADockerfile() {
        assertThat(ArchetypeCards.baseName(PassesTest.dockerfile("a", "t", "s", 1))).isEqualTo("ubi9/ubi-minimal:9.4");
        assertThat(ArchetypeCards.baseName(BaseCatalogueTest.dockerfile("a", new Base("ubi9", null, null), 1)))
                .isEqualTo("ubi9");
        assertThat(ArchetypeCards.baseName(null)).isEqualTo("an unknown base");
    }

    @Test
    void theLocationIsTheFirstOnesRepositoryAndPath() {
        assertThat(ArchetypeCards.location(List.of(new Location("org/a", "x/Dockerfile", "RELEASED"),
                new Location("org/b", "Dockerfile", "RELEASED")))).isEqualTo("org/a: x/Dockerfile");
        assertThat(ArchetypeCards.location(List.of())).isNull();
    }
}
