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
}
