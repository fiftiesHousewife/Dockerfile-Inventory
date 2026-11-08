package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ImageComparisonRunTest {

    private static final String ORIGINAL = "FROM ubi9\nCOPY bin/app /usr/local/bin/\nUSER   1001\n";

    @TempDir
    Path checkout;

    @TempDir
    Path work;

    @BeforeEach
    void repository() throws IOException {
        Files.createDirectories(checkout.resolve("images/app"));
        Files.writeString(checkout.resolve("images/app/Dockerfile"), ORIGINAL);
    }

    @Test
    void aPairBuiltFirstTimeFromTheDockerfilesDirectoryIsCompared() {
        FakeBuilder builder = new FakeBuilder((dockerfile, context) -> true);

        ImageComparison.Result result = new ImageComparisonRun(builder, work, "arm64")
                .compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertAll(
                () -> assertThat(result).isEqualTo(
                        new ImageComparison.Result("abc", true, null, true, List.of(), List.of(), List.of())),
                () -> assertThat(builder.calls).containsExactly("Dockerfile@images/app",
                        "regenerated.Dockerfile@images/app"));
    }

    @Test
    void aDockerfileThatBuildsOnlyFromTheRepositoryRootHasItsRegeneratedTextBuiltThereToo() {
        FakeBuilder builder = new FakeBuilder((dockerfile, context) -> context.equals(checkout));

        new ImageComparisonRun(builder, work, "arm64").compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(builder.calls).containsExactly("Dockerfile@images/app", "Dockerfile@",
                "regenerated.Dockerfile@");
    }

    @Test
    void aDockerfileWhoseSourcesCiMakesIsBuiltOnStubsListedInItsResult() {
        FakeBuilder builder = new FakeBuilder(
                (dockerfile, context) -> Files.exists(checkout.resolve("images/app/bin/app")));

        ImageComparison.Result result = new ImageComparisonRun(builder, work, "arm64")
                .compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(result.stubs()).containsExactly("bin/app");
    }

    @Test
    void anOriginalThatNeverBuildsIsNotBuiltWithItsReason() {
        ImageComparison.Result result = new ImageComparisonRun(new FakeBuilder((dockerfile, context) -> false), work,
                "arm64").compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(result.failure()).startsWith("the original does not build");
    }

    @Test
    void aRegeneratedDockerfileThatDoesNotBuildWhereTheOriginalDoesIsNotBuilt() {
        FakeBuilder builder = new FakeBuilder(
                (dockerfile, context) -> !String.valueOf(dockerfile.getFileName()).startsWith("regenerated"));

        ImageComparison.Result result = new ImageComparisonRun(builder, work, "arm64")
                .compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(result.failure()).startsWith("the regenerated Dockerfile does not build");
    }

    @Test
    void aDockerfileTheConverterCannotRenderIsNotBuilt() {
        ImageComparison.Result result = new ImageComparisonRun(new FakeBuilder((dockerfile, context) -> true), work,
                "arm64").compare("abc", "not a dockerfile\n", checkout, "images/app/Dockerfile");

        assertThat(result.failure()).isEqualTo("the converter cannot render it: the Dockerfile does not parse");
    }

    @Test
    void imagesThatCannotBeReadAreAResultNotAnEndToTheRun() {
        FakeBuilder builder = new FakeBuilder((dockerfile, context) -> true) {
            @Override
            public SortedMap<String, FileEntry> files(String tag) {
                throw new IllegalStateException("failed: docker exec tar");
            }
        };

        ImageComparison.Result result = new ImageComparisonRun(builder, work, "arm64")
                .compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(result.failure()).isEqualTo("the images could not be read: failed: docker exec tar");
    }

    private class FakeBuilder implements ImageComparisonRun.Builder {

        private final BiPredicate<Path, Path> builds;
        private final List<String> calls = new ArrayList<>();

        FakeBuilder(BiPredicate<Path, Path> builds) {
            this.builds = builds;
        }

        @Override
        public boolean build(Path dockerfile, Path context, String tag, Path log) {
            calls.add(dockerfile.getFileName() + "@" + checkout.relativize(context));
            return builds.test(dockerfile, context);
        }

        @Override
        public JsonNode inspect(String tag) {
            return new ObjectMapper().readTree("{\"Docker\": {\"config\": {\"User\": \"1001\"}}}");
        }

        @Override
        public SortedMap<String, FileEntry> files(String tag) {
            return new TreeMap<>();
        }
    }
}
