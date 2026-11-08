package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageCompareMainTest {

    @TempDir
    Path discovery;

    @TempDir
    Path work;

    @Test
    void renderWritesEachDockerfilesRegeneratedTextOrWhyItCannot() throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/good"), "FROM ubi9\nCOPY app.jar run.sh /opt/\nUSER   1001\n");
        Files.writeString(discovery.resolve("blobs/bad"), "not a dockerfile\n");

        ImageCompareMain.render(discovery, List.of("good", "bad"), work);

        assertAll(
                () -> assertThat(work.resolve("good/regenerated.Dockerfile"))
                        .hasContent("FROM ubi9\nCOPY app.jar run.sh /opt/\nUSER 1001\n"),
                () -> assertThat(work.resolve("good/sources.txt")).hasContent("app.jar\nrun.sh\n"),
                () -> assertThat(work.resolve("bad/failure.txt"))
                        .hasContent("the converter cannot render it: the Dockerfile does not parse"));
    }

    @Test
    void compareWritesOneLinePerDockerfileWithTheContentItIgnores() {
        List<String> lines = ImageCompareMain.compare(Path.of("src/test/resources/fixtures/comparison/images"))
                .lines().toList();

        assertAll(
                () -> assertThat(lines).hasSize(3),
                () -> assertThat(lines.getFirst()).startsWith("{\"blobSha\":\"aaa\",\"built\":true")
                        .contains("\"same\":true").contains("\"ignoredContent\":[\"/var/lib/rpm/\""));
    }

    @Test
    void theStepIsRenderOrCompareAndAnythingElseIsRefusedByName() {
        assertAll(
                () -> assertThat(ImageCompareMain.step("render")).isEqualTo(ImageCompareMain.Step.RENDER),
                () -> assertThat(ImageCompareMain.step("compare")).isEqualTo(ImageCompareMain.Step.COMPARE),
                () -> assertThatIllegalStateException().isThrownBy(() -> ImageCompareMain.step("build"))
                        .withMessageContaining("STEP"));
    }
}
