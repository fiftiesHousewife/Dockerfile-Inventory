package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogueViewTest {

    @Test
    void eachEntryIsReadWithTheBasesItsStagesPinByDigest(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("catalogue"));
        Files.writeString(dir.resolve("catalogue/dockerfiles.jsonl"), """
                {"blobSha":"app","build":{"stages":[\
                {"baseName":"golang","baseTag":"1.25","baseDigest":"sha256:aa"},\
                {"baseName":"ubi9/ubi-minimal","baseTag":null,"baseDigest":"sha256:bb"},\
                {"baseName":"alpine","baseTag":"3","baseDigest":null},{"baseName":null,"fromStage":"build"}]}}
                {"blobSha":"broken","build":null}
                """);

        assertThat(CatalogueView.read(dir)).containsExactly(
                new CatalogueView.Entry("app", List.of(new CatalogueView.Pinned("golang", "1.25", "sha256:aa"),
                        new CatalogueView.Pinned("ubi9/ubi-minimal", null, "sha256:bb"))),
                new CatalogueView.Entry("broken", List.of()));
    }
}
