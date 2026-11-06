package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InventoryTest {

    static final String DOCKER_MANIFEST = "application/vnd.docker.distribution.manifest.v2+json";
    static final String CONFIG_C = "sha256:" + "c".repeat(64);

    @TempDir
    Path out;

    @Test
    void everyTagIsRecordedWithItsConfigDigestAndLastPushAndItsConfigIsStored() throws IOException {
        FakeArtifactory artifactory = new FakeArtifactory()
                .tag("docker-local", "team/app", "1.0", "2026-10-02T10:15:00Z", DOCKER_MANIFEST, "manifest-image.json")
                .blob(CONFIG_C, "config.json");
        LineageFiles files = new LineageFiles(out);

        new Inventory(artifactory, files, 1).run();

        assertThat(files.tags()).containsExactly(new TagRow("docker-local", "team/app", "1.0",
                Instant.parse("2026-10-02T10:15:00Z"), CONFIG_C, null, null));
        assertThat(files.config(CONFIG_C)).hasValueSatisfying(text -> assertThat(text).contains("diff_ids"));
    }

    @Test
    void aSkippedTagIsRecordedWithItsReason() {
        FakeArtifactory artifactory = new FakeArtifactory().tag("docker-local", "team/arm", "1.0",
                "2026-10-01T00:00:00Z", "application/vnd.docker.distribution.manifest.list.v2+json",
                "list-arm-only.json");
        LineageFiles files = new LineageFiles(out);

        new Inventory(artifactory, files, 1).run();

        assertThat(files.tags()).singleElement().satisfies(row -> {
            assertThat(row.configDigest()).isNull();
            assertThat(row.skipped()).isEqualTo(Resolved.Reason.NO_AMD64);
        });
    }

    @Test
    void aConfigIsFetchedOnceHoweverManyTagsAndRepositoriesShareIt() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .tag("docker-dev", "team/app", "1.0", "2026-10-01T00:00:00Z", DOCKER_MANIFEST, "manifest-image.json")
                .tag("docker-dev", "team/app", "latest", "2026-10-01T00:00:00Z", DOCKER_MANIFEST, "manifest-image.json")
                .tag("docker-prod", "team/app", "1.0", "2026-10-03T00:00:00Z", DOCKER_MANIFEST, "manifest-image.json")
                .blob(CONFIG_C, "config.json");
        LineageFiles files = new LineageFiles(out);

        new Inventory(artifactory, files, 1).run();

        assertThat(files.tags()).hasSize(3).allSatisfy(row -> assertThat(row.configDigest()).isEqualTo(CONFIG_C));
        assertThat(artifactory.blobCalls.get(CONFIG_C)).hasValue(1);
    }

    @Test
    void aRerunFetchesNoManifestForATagAlreadyRecorded() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .tag("docker-local", "team/app", "1.0", "2026-10-01T00:00:00Z", DOCKER_MANIFEST, "manifest-image.json")
                .blob(CONFIG_C, "config.json");
        LineageFiles files = new LineageFiles(out);
        new Inventory(artifactory, files, 1).run();
        int manifestsBefore = artifactory.manifestCalls.get();

        new Inventory(artifactory, files, 1).run();

        assertThat(artifactory.manifestCalls).hasValue(manifestsBefore);
        assertThat(files.tags()).hasSize(1);
        assertThat(artifactory.blobCalls.get(CONFIG_C)).hasValue(1);
    }

    @Test
    void severalThreadsRecordTheSameRowsAsOne() {
        FakeArtifactory artifactory = new FakeArtifactory().blob(CONFIG_C, "config.json");
        for (int i = 0; i < 20; i++) {
            artifactory.tag("docker-local", "team/app-" + i, "1.0", "2026-10-01T00:00:00Z", DOCKER_MANIFEST,
                    "manifest-image.json");
        }
        LineageFiles files = new LineageFiles(out);

        new Inventory(artifactory, files, 8).run();

        assertThat(files.tags()).hasSize(20).extracting(TagRow::image).doesNotHaveDuplicates();
        assertThat(artifactory.blobCalls.get(CONFIG_C)).hasValue(1);
    }
}
