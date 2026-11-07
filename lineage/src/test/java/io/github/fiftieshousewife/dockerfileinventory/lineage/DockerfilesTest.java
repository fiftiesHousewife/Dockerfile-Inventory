package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DockerfilesTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<JsonNode> LOCATIONS = List.of(
            JSON.readTree("{\"repository\":\"team/a\",\"scope\":\"RELEASED\",\"blobSha\":\"one\"}"),
            JSON.readTree("{\"repository\":\"team/b\",\"scope\":\"DEFAULT_BRANCH\",\"blobSha\":\"one\"}"),
            JSON.readTree("{\"repository\":\"team/c\",\"scope\":\"DEFAULT_BRANCH\",\"blobSha\":\"two\"}"));

    @Test
    void aBlobIsReleasedWhenAnyOfItsLocationsIsAtAReleasedCommit() {
        assertThat(Dockerfiles.released(LOCATIONS)).containsExactly("one");
    }

    @Test
    void eachBlobListsEveryRepositoryThatHoldsIt() {
        assertThat(Dockerfiles.repositories(LOCATIONS))
                .isEqualTo(Map.of("one", Set.of("team/a", "team/b"), "two", Set.of("team/c")));
    }

    @Test
    void aFactsRowGivesItsStepsBasePathReleaseAndRepositories() {
        JsonNode row = JSON.readTree(
                "{\"blobSha\":\"one\",\"fromPath\":\"ubi9\",\"steps\":[{\"keyword\":\"RUN\",\"text\":\"make\"}]}");

        assertThat(Dockerfiles.dockerfile(row, Set.of("one"), Map.of("one", Set.of("team/a"))))
                .isEqualTo(new Dockerfile("one", List.of(new Instruction("RUN", "make")), "ubi9", true,
                        Set.of("team/a")));
    }

    @Test
    void aRowWithoutBasePathOrLocationsHasNeither() {
        JsonNode row = JSON.readTree("{\"blobSha\":\"lost\",\"steps\":[]}");

        assertThat(Dockerfiles.dockerfile(row, Set.of(), Map.of()))
                .isEqualTo(new Dockerfile("lost", List.of(), null, false, Set.of()));
    }

    @Test
    void readsEveryFactsRowAndMissingFilesReadAsNone(@TempDir Path facts, @TempDir Path discovery)
            throws IOException {
        assertThat(Dockerfiles.read(facts, discovery)).isEmpty();

        Files.writeString(facts.resolve("facts.jsonl"), "{\"blobSha\":\"one\",\"steps\":[]}\n\n");

        assertThat(Dockerfiles.read(facts, discovery)).extracting(Dockerfile::blobSha).containsExactly("one");
    }
}
