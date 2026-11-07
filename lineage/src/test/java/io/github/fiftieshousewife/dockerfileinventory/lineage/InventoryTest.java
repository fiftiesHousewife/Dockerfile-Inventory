package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
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
    void aRepositoryOrImageTheTokenMayNotReadIsRecordedAsRefusedAndTheRestIsRead() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .tag("docker-local", "team/app", "1.0", "2026-10-01T00:00:00Z",
                        "application/vnd.docker.distribution.manifest.list.v2+json", "list-arm-only.json")
                .refuse("secret-local")
                .refuse("docker-local", "team/private");
        LineageFiles files = new LineageFiles(out);

        new Inventory(artifactory, files, 1).run();

        assertThat(files.tags()).extracting(TagRow::repository, TagRow::image, TagRow::skipped)
                .containsExactlyInAnyOrder(
                        tuple("docker-local", "team/app", Resolved.Reason.NO_AMD64),
                        tuple("docker-local", "team/private", Resolved.Reason.REFUSED),
                        tuple("secret-local", "*", Resolved.Reason.REFUSED));
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

    @Test
    void aTagRowNamesItsConfigOrWhyItWasSkipped() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .tag("docker-local", "team/app", "1.0", "2026-10-01T00:00:00Z", DOCKER_MANIFEST, "manifest-image.json")
                .blob(CONFIG_C, "config.json");
        Inventory inventory = new Inventory(artifactory, new LineageFiles(out), 1);

        assertThat(inventory.row("docker-local", "team/app", "1.0")).isEqualTo(new TagRow("docker-local", "team/app",
                "1.0", Instant.parse("2026-10-01T00:00:00Z"), CONFIG_C, null, null));
        assertThat(inventory.row("docker-local", "team/app", "gone")).isEqualTo(new TagRow("docker-local", "team/app",
                "gone", null, null, Resolved.Reason.GONE, "docker-local/team/app:gone"));
    }

    @Test
    void aConfigThatIsGoneOrUnreadableIsNotStoredAndSaysWhy() {
        FakeArtifactory artifactory = new FakeArtifactory().blob("sha256:bad", "not-json.txt");
        LineageFiles files = new LineageFiles(out);
        Inventory inventory = new Inventory(artifactory, files, 1);

        assertThat(inventory.fetch("docker-local", "team/app", "sha256:gone"))
                .contains(new Resolved.Skipped(Resolved.Reason.GONE, "sha256:gone"));
        assertThat(inventory.fetch("docker-local", "team/app", "sha256:bad"))
                .contains(new Resolved.Skipped(Resolved.Reason.UNREADABLE_CONFIG, "sha256:bad"));
        assertThat(files.hasConfig("sha256:bad")).isFalse();
    }

    @Test
    void aConfigAlreadyStoredIsNotFetched() {
        FakeArtifactory artifactory = new FakeArtifactory();
        LineageFiles files = new LineageFiles(out);
        files.storeConfig(CONFIG_C, "{}");

        assertThat(new Inventory(artifactory, files, 1).config("docker-local", "team/app", CONFIG_C)).isEmpty();
        assertThat(artifactory.blobCalls).isEmpty();
    }

    @Test
    void anImagesTagsAlreadyDoneAreNotRecordedAgain() {
        FakeArtifactory artifactory = new FakeArtifactory()
                .tag("docker-local", "team/app", "1.0", "2026-10-01T00:00:00Z", DOCKER_MANIFEST, "manifest-image.json")
                .tag("docker-local", "team/app", "2.0", "2026-10-01T00:00:00Z", DOCKER_MANIFEST, "manifest-image.json")
                .blob(CONFIG_C, "config.json");
        LineageFiles files = new LineageFiles(out);

        new Inventory(artifactory, files, 1).image("docker-local", "team/app", Set.of("docker-local/team/app:1.0"));

        assertThat(files.tags()).extracting(TagRow::tag).containsExactly("2.0");
    }

    @Test
    void aLimitedReadReleasesItsPermitWhenItEnds() throws InterruptedException {
        FakeArtifactory artifactory = new FakeArtifactory();
        Semaphore permits = new Semaphore(1);

        new Inventory(artifactory, new LineageFiles(out), 1).limited(permits, "docker-local", "team/app", Set.of());

        assertThat(permits.availablePermits()).isEqualTo(1);
    }

    @Test
    void aFailedReadFailsTheRunWithItsCause() {
        IllegalArgumentException cause = new IllegalArgumentException("boom");

        assertThatIllegalStateException()
                .isThrownBy(() -> Inventory.awaitAll(List.of(CompletableFuture.failedFuture(cause))))
                .withMessage("reading an image failed")
                .withCause(cause);
    }
}
