package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageComparisonTest {

    private static final Path IMAGES = Path.of("src/test/resources/fixtures/comparison/images");

    @Test
    void twoImagesDifferingOnlyInWhatIsIgnoredAreTheSame() {
        assertThat(ImageComparison.of(IMAGES.resolve("aaa")))
                .isEqualTo(new ImageComparison.Result("aaa", true, null, true, List.of(), List.of(), List.of()));
    }

    @Test
    void eachDifferenceInConfigAndFilesIsListed() {
        assertThat(ImageComparison.of(IMAGES.resolve("bbb"))).isEqualTo(new ImageComparison.Result("bbb", true, null,
                false, List.of("User: \"1001\" became \"0\""), List.of("/app: mode 755 became 644"),
                List.of("bin/app")));
    }

    @Test
    void aBuildThatFailedSaysWhy() {
        assertThat(ImageComparison.of(IMAGES.resolve("ccc"))).isEqualTo(new ImageComparison.Result("ccc", false,
                "the original does not build with its directory or the repository root as context", false, List.of(),
                List.of(), List.of()));
    }

    @Test
    void theConfigIsDockersOrElseTheOciOne(@TempDir Path image) throws IOException {
        Files.writeString(image.resolve("inspect.json"), "{\"OCIv1\": {\"config\": {\"User\": \"1001\"}}}");

        assertThat(ImageComparison.config(image).toString()).isEqualTo("{\"User\":\"1001\"}");
    }
}
