package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageCollectTest {

    private static final String DIGEST = "sha256:" + "1a2b3c4d".repeat(8);

    @TempDir
    Path run;

    @TempDir
    Path corpus;

    @BeforeEach
    void aLineageRun() throws IOException {
        Files.createDirectories(run.resolve("lineage/configs"));
        Files.writeString(run.resolve("lineage/images.jsonl"), "{\"image\":\"okd/scos/tools\",\"configDigest\":\""
                + DIGEST + "\"}\n{\"image\":\"gone\",\"configDigest\":null}\n");
        Files.writeString(run.resolve("lineage/configs/" + DIGEST.replace(':', '_') + ".json"), "{\"architecture\":"
                + "\"amd64\",\"config\":{\"User\":\"1001\",\"Labels\":{\"io.openshift.build.source-location\":"
                + "\"https://github.com/openshift/oc\"}},\"rootfs\":{\"diff_ids\":[\"sha256:aa\"]},\"history\":[]}");
        Files.writeString(run.resolve("lineage/links.jsonl"), "{\"configDigest\":\"" + DIGEST + "\",\"tier\":"
                + "\"WEAK\",\"blobShas\":[\"df1\"],\"parentMissing\":false,\"credited\":true}\n");
        Files.createDirectories(run.resolve("facts"));
        Files.writeString(run.resolve("facts/facts.jsonl"), "{\"blobSha\":\"df1\",\"steps\":[{\"keyword\":\"RUN\","
                + "\"text\":\"make\"}],\"fromPath\":\"ocp/5.0\",\"facts\":{}}\n{\"blobSha\":\"other\",\"steps\":[],"
                + "\"fromPath\":null}\n");
        Files.createDirectories(run.resolve("discovery"));
        Files.writeString(run.resolve("discovery/manifest.jsonl"), "{\"repository\":\"openshift/oc\",\"blobSha\":"
                + "\"df1\"}\n{\"repository\":\"openshift/other\",\"blobSha\":\"other\"}\n");
    }

    @Test
    void eachImageBecomesACaseWithItsConfigItsRepositorysDockerfilesAndItsLink() throws IOException {
        new ImageCollect(run, corpus).collect();

        Path cased = corpus.resolve("scos-tools-1a2b3c4d");
        assertAll(
                () -> assertThat(cased.resolve("config.json")).content().contains("\"User\": \"1001\"")
                        .contains("\"diff_ids\"").doesNotContain("architecture"),
                () -> assertThat(cased.resolve("dockerfiles.jsonl")).hasContent("{\"blobSha\":\"df1\",\"steps\":"
                        + "[{\"keyword\":\"RUN\",\"text\":\"make\"}],\"fromPath\":\"ocp/5.0\",\"repository\":"
                        + "\"openshift/oc\"}\n"),
                () -> assertThat(cased.resolve("expected.json")).content().contains("\"image\": \"scos-tools\"",
                        "\"sourceRepository\": \"openshift/oc\"", "\"tier\": \"WEAK\"", "\"credited\": true"));
    }

    @Test
    void anExistingCaseIsKeptAndAnImageWithoutItsConfigIsLeftOut() throws IOException {
        Files.createDirectories(corpus.resolve("scos-tools-1a2b3c4d"));

        new ImageCollect(run, corpus).collect();

        try (Stream<Path> files = Files.list(corpus.resolve("scos-tools-1a2b3c4d"))) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void anImagePushedUnderTwoNamesTakesTheFirstTheRunRecorded() throws IOException {
        Files.writeString(run.resolve("lineage/images.jsonl"), "{\"image\":\"okd/tools\",\"configDigest\":\""
                + DIGEST + "\"}\n{\"image\":\"art/ocp-art-tenant/art-okd-images\",\"configDigest\":\"" + DIGEST
                + "\"}\n");

        assertThat(new ImageCollect(run, corpus).names()).containsEntry(DIGEST, "okd-tools");
    }

    @Test
    void anImagesNameIsTheLastTwoPartsOfItsPath() {
        assertAll(
                () -> assertThat(ImageCollect.name("art/ocp-art-tenant/art-okd-images"))
                        .isEqualTo("ocp-art-tenant-art-okd-images"),
                () -> assertThat(ImageCollect.name("tools")).isEqualTo("tools"));
    }
}
