package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoiseTest {

    @Test
    void aDifferenceTheControlAlsoShowsIsSetAsideByFieldPathOrDirectory() {
        Noise noise = Noise.of(List.of("label build-date: \"1\" became \"2\"",
                "/usr/local/bin/php: content differs",
                "/tmp/hsperfdata_root/29423: only in the original"));

        assertThat(noise.removedFrom(List.of("label build-date: \"1\" became \"3\"",
                "User: \"1001\" became \"0\"",
                "/usr/local/bin/php: content differs",
                "/usr/local/bin/php: mode 755 became 644",
                "/tmp/hsperfdata_root/30251: only in the regenerated",
                "/opt/app/app.jar: only in the original")))
                .containsExactly("User: \"1001\" became \"0\"", "/usr/local/bin/php: mode 755 became 644",
                        "/opt/app/app.jar: only in the original");
    }

    @Test
    void aDifferencesKeyIsAFieldOrAPathWithWhatAboutItDiffers() {
        assertAll(
                () -> assertThat(Noise.key("User: \"1001\" became \"0\"")).isEqualTo("User"),
                () -> assertThat(Noise.key("label build-date: \"1\" became \"2\"")).isEqualTo("label build-date"),
                () -> assertThat(Noise.key("/bin/php: mode 755 became 644")).isEqualTo("/bin/php: mode"),
                () -> assertThat(Noise.key("/bin/php: content differs")).isEqualTo("/bin/php: content"));
    }

    @Test
    void aFileInOneImageOnlyIsMatchedByItsDirectory() {
        assertAll(
                () -> assertThat(Noise.onlyInOne("/tmp/x/1: only in the original")).isTrue(),
                () -> assertThat(Noise.onlyInOne("/tmp/x/1: content differs")).isFalse(),
                () -> assertThat(Noise.directory("/tmp/x/1: only in the original")).isEqualTo("/tmp/x"),
                () -> assertThat(Noise.directory("/app: only in the regenerated")).isEqualTo("/"));
    }
}
