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
                () -> assertThat(result).isEqualTo(new ImageComparison.Result("abc", true, null, true, List.of(),
                        List.of(), List.of(), true, List.of())),
                () -> assertThat(builder.calls).containsExactly("Dockerfile@images/app",
                        "regenerated.Dockerfile@images/app", "Dockerfile@images/app"));
    }

    @Test
    void aDockerfileThatBuildsOnlyFromTheRepositoryRootHasItsRegeneratedTextBuiltThereToo() {
        FakeBuilder builder = new FakeBuilder((dockerfile, context) -> context.equals(checkout));

        new ImageComparisonRun(builder, work, "arm64").compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(builder.calls).containsExactly("Dockerfile@images/app", "Dockerfile@",
                "regenerated.Dockerfile@", "Dockerfile@");
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
    void bothBuildsAreGivenTheSecretsTheDockerfileMounts() {
        FakeBuilder builder = new FakeBuilder((dockerfile, context) -> true);

        new ImageComparisonRun(builder, work, "arm64").compare("abc",
                "FROM ubi9\nRUN --mount=type=secret,id=downloads_url make\n", checkout, "images/app/Dockerfile");

        assertThat(builder.secretsGiven).containsOnly(List.of("downloads_url"));
    }

    @Test
    void aControlThatDoesNotBuildLeavesTheComparisonUncontrolled() {
        FakeBuilder builder = new FakeBuilder((dockerfile, context) -> true) {
            @Override
            public BuildOutcome build(Path dockerfile, Path context, String tag, Path log, List<String> secrets) {
                BuildOutcome outcome = super.build(dockerfile, context, tag, log, secrets);
                return tag.endsWith(":control") ? BuildOutcome.FAILED : outcome;
            }
        };

        ImageComparison.Result result = new ImageComparisonRun(builder, work, "arm64")
                .compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(result.controlled()).isFalse();
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

    @Test
    void anOriginalFailingOtherThanOnItsSourcesIsBuiltOnceAndNotBuiltWithItsReason() {
        FakeBuilder builder = new FakeBuilder((dockerfile, context) -> false, BuildOutcome.FAILED);

        ImageComparison.Result result = new ImageComparisonRun(builder, work, "arm64")
                .compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertAll(
                () -> assertThat(builder.calls).containsExactly("Dockerfile@images/app"),
                () -> assertThat(result.failure()).isEqualTo(
                        "the original does not build; see original-build-directory.log"));
    }

    @Test
    void anOriginalOverTheTimeLimitSaysItTimedOut() {
        ImageComparison.Result result = new ImageComparisonRun(new FakeBuilder((dockerfile, context) -> false,
                BuildOutcome.TIMED_OUT), work, "arm64").compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(result.failure()).isEqualTo(
                "the original took longer than BUILD_TIMEOUT; see original-build-directory.log");
    }

    @Test
    void aRegeneratedBuildOverTheTimeLimitSaysItTimedOut() {
        FakeBuilder builder = new FakeBuilder(
                (dockerfile, context) -> !String.valueOf(dockerfile.getFileName()).startsWith("regenerated"),
                BuildOutcome.TIMED_OUT);

        ImageComparison.Result result = new ImageComparisonRun(builder, work, "arm64")
                .compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(result.failure()).isEqualTo(
                "the regenerated Dockerfile took longer than BUILD_TIMEOUT; see regenerated-build.log");
    }

    @Test
    void aPairsImagesAreRemovedOnceCompared() {
        FakeBuilder builder = new FakeBuilder((dockerfile, context) -> true);

        new ImageComparisonRun(builder, work, "arm64").compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(builder.removed).containsExactly("compare/abc:original", "compare/abc:regenerated",
                "compare/abc:control");
    }

    @Test
    void aPairsImagesAreRemovedWhenItIsNotBuilt() {
        FakeBuilder builder = new FakeBuilder((dockerfile, context) -> false, BuildOutcome.FAILED);

        new ImageComparisonRun(builder, work, "arm64").compare("abc", ORIGINAL, checkout, "images/app/Dockerfile");

        assertThat(builder.removed).containsExactly("compare/abc:original", "compare/abc:regenerated",
                "compare/abc:control");
    }

    private class FakeBuilder implements ImageComparisonRun.Builder {

        private final BiPredicate<Path, Path> builds;
        private final BuildOutcome otherwise;
        private final List<String> calls = new ArrayList<>();
        private final List<List<String>> secretsGiven = new ArrayList<>();
        private final List<String> removed = new ArrayList<>();

        FakeBuilder(BiPredicate<Path, Path> builds) {
            this.builds = builds;
            this.otherwise = BuildOutcome.MISSING_SOURCE;
        }

        FakeBuilder(BiPredicate<Path, Path> builds, BuildOutcome otherwise) {
            this.builds = builds;
            this.otherwise = otherwise;
        }

        @Override
        public BuildOutcome build(Path dockerfile, Path context, String tag, Path log, List<String> secrets) {
            calls.add(dockerfile.getFileName() + "@" + checkout.relativize(context));
            secretsGiven.add(secrets);
            return builds.test(dockerfile, context) ? BuildOutcome.BUILT : otherwise;
        }

        @Override
        public void remove(List<String> tags) {
            removed.addAll(tags);
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
