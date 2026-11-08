package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageComparisonTest {

    private static final Path IMAGES = Path.of("src/test/resources/fixtures/comparison/images");

    @Test
    void twoImagesDifferingOnlyInWhatIsIgnoredAreTheSame() {
        assertThat(ImageComparison.of(IMAGES.resolve("aaa")))
                .isEqualTo(new ImageComparison.Result("aaa", true, null, true, List.of(), List.of()));
    }

    @Test
    void eachDifferenceInConfigAndFilesIsListed() {
        assertThat(ImageComparison.of(IMAGES.resolve("bbb"))).isEqualTo(new ImageComparison.Result("bbb", true, null,
                false, List.of("User: \"1001\" became \"0\""), List.of("/app: mode 755 became 644")));
    }

    @Test
    void aBuildThatFailedSaysWhy() {
        assertThat(ImageComparison.of(IMAGES.resolve("ccc"))).isEqualTo(new ImageComparison.Result("ccc", false,
                "the original does not build with its directory or the repository root as context", false, List.of(),
                List.of()));
    }
}
