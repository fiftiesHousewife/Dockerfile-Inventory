package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnmaintainedTest {

    private static final Instant NEWEST_PUSH = Instant.parse("2026-11-06T18:18:06.098Z");

    private static RegistryImage pushed(String lastPushed, String newerBase) {
        return new RegistryImage("sha256:d071", List.of("sclorg/s2i-python-container-3-12-c9s:c9s"), "sha256:bf34",
                List.of(), 5, "2026-11-06T18:18:01Z", lastPushed, newerBase, Map.of());
    }

    private static CatalogueEntry.Place in(String repository) {
        return new CatalogueEntry.Place(repository, "Dockerfile", "release", "abc", null, null);
    }

    @Test
    void anImageNotRebuiltOnItsUpdatedBaseNamesTheNewerBase() {
        assertThat(Unmaintained.reasons(pushed("2026-11-06T18:18:06.098Z", "sha256:0b1c"), NEWEST_PUSH, 12))
                .containsExactly("not rebuilt on its updated base sha256:0b1c");
    }

    @Test
    void anImageNotPublishedForLongerThanTheThresholdBeforeTheNewestPushIsStale() {
        assertThat(Unmaintained.reasons(pushed("2025-11-06T18:18:05Z", null), NEWEST_PUSH, 12))
                .containsExactly("not published since 2025-11-06");
    }

    @Test
    void anImagePublishedWithinTheThresholdIsMaintained() {
        assertThat(Unmaintained.reasons(pushed("2025-11-06T18:18:07Z", null), NEWEST_PUSH, 12)).isEmpty();
    }

    @Test
    void aDockerfileWhoseRepositoriesAreAllArchivedIsUnmaintained() {
        assertThat(Unmaintained.archived(List.of(in("acme/old"), in("acme/older")), Set.of("acme/old", "acme/older")))
                .containsExactly("every repository holding it is archived");
    }

    @Test
    void aDockerfileStillInOneLiveRepositoryIsMaintained() {
        assertThat(Unmaintained.archived(List.of(in("acme/old"), in("acme/live")), Set.of("acme/old"))).isEmpty();
    }

    @Test
    void aDockerfileWithNoLocationsIsNotCalledArchived() {
        assertThat(Unmaintained.archived(List.of(), Set.of("acme/old"))).isEmpty();
    }

    @Test
    void theArchivedRepositoriesAreReadFromDiscovery(@TempDir Path discovery) throws IOException {
        Files.writeString(discovery.resolve("repos.jsonl"), """
                {"repository":"openshift/test-pull-requests","status":"SCANNED","archived":true}
                {"repository":"openshift/python-interface","status":"SCANNED","archived":false}
                """);

        assertThat(Unmaintained.archivedRepositories(discovery)).containsExactly("openshift/test-pull-requests");
    }
}
