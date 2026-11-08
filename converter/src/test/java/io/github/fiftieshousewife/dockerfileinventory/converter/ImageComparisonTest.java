package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ImageComparisonTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void twoImagesDifferingOnlyInWhatIsIgnoredAreTheSame() {
        assertThat(ImageComparison.compared("aaa", inspect("1001", "1.37.0"), inspect("1001", "1.37.1"),
                files("644", "s1"), files("644", "s1"), List.of(), Optional.empty()))
                .isEqualTo(new ImageComparison.Result("aaa", true, null, true, List.of(), List.of(), List.of(), false,
                        List.of()));
    }

    @Test
    void eachDifferenceInConfigAndFilesIsListedWithTheStubsBuiltOn() {
        assertThat(ImageComparison.compared("bbb", inspect("1001", "1"), inspect("0", "1"), files("755", "s1"),
                files("644", "s1"), List.of("bin/app"), Optional.empty()))
                .isEqualTo(new ImageComparison.Result("bbb", true, null, false, List.of("User: \"1001\" became \"0\""),
                        List.of("/app: mode 755 became 644"), List.of("bin/app"), false, List.of()));
    }

    @Test
    void whatTheTwoOriginalsAlsoDifferInIsSetAsideAsNoiseAndListed() {
        ImageComparison.Control control = new ImageComparison.Control(inspect("1001", "1"), files("755", "s3"));

        assertThat(ImageComparison.compared("ddd", inspect("1001", "1"), inspect("1001", "1"), files("755", "s1"),
                files("755", "s2"), List.of(), Optional.of(control)))
                .isEqualTo(new ImageComparison.Result("ddd", true, null, true, List.of(), List.of(), List.of(), true,
                        List.of("/app: content differs")));
    }

    @Test
    void aPairThatDidNotBuildSaysWhy() {
        assertThat(ImageComparison.notBuilt("ccc", "the original does not build"))
                .isEqualTo(new ImageComparison.Result("ccc", false, "the original does not build", false, List.of(),
                        List.of(), List.of(), false, List.of()));
    }

    @Test
    void theConfigIsDockersOrElseTheOciOne() {
        assertThat(ImageComparison.config(JSON.readTree("{\"OCIv1\": {\"config\": {\"User\": \"1001\"}}}")).toString())
                .isEqualTo("{\"User\":\"1001\"}");
    }

    private static JsonNode inspect(String user, String buildah) {
        return JSON.readTree("{\"Docker\": {\"config\": {\"User\": \"" + user
                + "\", \"Labels\": {\"io.buildah.version\": \"" + buildah + "\"}}}}");
    }

    private static SortedMap<String, FileEntry> files(String mode, String sha) {
        return new TreeMap<>(Map.of("/app", new FileEntry("f", mode, "0", "0", "", sha)));
    }
}
