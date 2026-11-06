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
    void aSignatureReadsAsASentenceNamingTheFirstPackagesAndCountingTheRest() {
        assertThat(ArchetypeCards.sentence(
                "s2i-base-c9s:|yum|hostname,mod_ssl,php,php-fpm,php-gd,tzdata|NUMERIC|8080,8443|EXEC|stages=1",
                "quay.io/sclorg/s2i-base-c9s:c9s"))
                .isEqualTo("Builds on quay.io/sclorg/s2i-base-c9s:c9s, installs 6 packages with yum (hostname, "
                        + "mod_ssl, php, php-fpm and 2 more), runs as a numeric user, exposes 8080 and 8443, "
                        + "starts with an exec-form command, in one stage.");
    }

    @Test
    void noPackagesRootNoPortsShellFormAndSeveralStagesReadPlainlyToo() {
        assertThat(ArchetypeCards.sentence("golang:1|apt-get|jq|ROOT||SHELL|stages=2", "golang:1.22"))
                .isEqualTo("Builds on golang:1.22, installs 1 package with apt-get (jq), runs as root, exposes no "
                        + "ports, "
                        + "starts with a shell-form command, in 2 stages.");
        assertThat(ArchetypeCards.sentence("scratch:|||UNRESOLVED||NONE|stages=1", "scratch"))
                .isEqualTo("Builds on scratch, installs no packages, runs as a user set by a variable, exposes no "
                        + "ports, sets no start command of its own, in one stage.");
    }

    @Test
    void templatesAndUnreadableFilesHaveTheirOwnSentence() {
        assertThat(ArchetypeCards.sentence("templates", null))
                .isEqualTo("Templates saved under a Dockerfile's name (Jinja and similar), not Dockerfiles.");
        assertThat(ArchetypeCards.sentence("parse-failures", null))
                .isEqualTo("Dockerfiles the parser could not read.");
    }

    @Test
    void aCardHasItsSentenceSizeRepositoriesAndTheRepresentativesLocationAndText(@TempDir Path discovery)
            throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/b1"), "FROM ubi9/ubi-minimal:9.4\nUSER 1001\n");
        String signature = "ubi-minimal:9|||NUMERIC||NONE|stages=1";
        Dockerfile file = new Dockerfile("b1", "t", "s", signature, new Base("ubi9/ubi-minimal", "9.4", null),
                Set.of(), Set.of(), "NUMERIC", 0, 0,
                List.of(new Location("org/app", "Dockerfile", "RELEASED"),
                        new Location("org/api", "Dockerfile", "RELEASED")));
        Estate estate = new Estate(List.of(file), List.of(), List.of(), Map.of());
        List<Archetype> archetypes = IntStream.range(0, 11).mapToObj(i -> new Archetype(i + 1, "k" + i, 2,
                List.of("b1", "b2"), signature, i == 0 ? "b1" : "gone", List.of("org/api", "org/app"))).toList();

        List<ArchetypeCards.Card> cards = ArchetypeCards.of(new Ranking(archetypes, 22, 1, 1), estate, discovery);

        assertThat(cards).hasSize(10);
        assertThat(cards.getFirst()).isEqualTo(new ArchetypeCards.Card(1,
                "Builds on ubi9/ubi-minimal:9.4, installs no packages, runs as a numeric user, exposes no ports, "
                        + "sets no start command of its own, in one stage.",
                2, 2, List.of("org/api", "org/app"), "org/app: Dockerfile",
                "FROM ubi9/ubi-minimal:9.4\nUSER 1001\n"));
        assertThat(cards.get(1).exampleText()).isNull();
    }
}
