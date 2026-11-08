package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("image-roundtrip")
class ImageRoundTripTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path OUT = Path.of("build/image-round-trip");

    @Test
    void eachChosenDockerfilesRegeneratedTextBuildsTheSameImageAsTheOriginal() throws IOException {
        Path discovery = Path.of(System.getenv().getOrDefault("ROUND_TRIP_DISCOVERY_DIR", "../out/all/discovery"));
        assumeTrue(Files.isDirectory(discovery.resolve("blobs")), "no estate at " + discovery.toAbsolutePath());
        assumeTrue(dockerRuns(), "Docker is not reachable");
        List<JsonNode> manifest = Files.readAllLines(discovery.resolve("manifest.jsonl")).stream()
                .map(JSON::readTree).toList();
        Path chosen = Path.of(System.getenv().getOrDefault("IMAGE_ROUND_TRIP_DOCKERFILES",
                "src/test/resources/estate/image-round-trip.txt"));
        List<String> shas = DockerfileSelection.shas(Files.readAllLines(chosen), manifest);
        Map<String, JsonNode> locations = manifest.stream().collect(Collectors.toMap(
                row -> row.path("blobSha").asString(), Function.identity(), (first, second) -> first));

        List<ImageComparison.Result> results = new ArrayList<>();
        Checkouts checkouts = new Checkouts(OUT.resolve("src"), CloneSource.from(System.getenv()));
        try (BuildahContainer buildah = BuildahContainer.start(OUT.resolve("src"), OUT.resolve("work"),
                System.getenv())) {
            ImageComparisonRun run = new ImageComparisonRun(buildah, OUT.resolve("work"), buildah.arch());
            shas.forEach(sha -> results.add(compare(sha, locations.get(sha), discovery, checkouts, run)));
        }
        Files.writeString(OUT.resolve("comparison.jsonl"), results.stream().map(JSON::writeValueAsString)
                .collect(Collectors.joining("\n", "", "\n")));
        results.forEach(result -> System.out.println(summary(result)));

        assertAll(Stream.concat(
                Stream.<Executable>of(() -> assertThat(results).as("pairs built")
                        .anyMatch(ImageComparison.Result::built)),
                results.stream().filter(ImageComparison.Result::built).map(result -> () -> assertThat(result.same())
                        .as(result.blobSha() + ": " + result.config() + " " + result.files()).isTrue())));
    }

    private static ImageComparison.Result compare(String sha, JsonNode location, Path discovery, Checkouts checkouts,
            ImageComparisonRun run) {
        if (location == null) {
            return ImageComparison.notBuilt(sha, "not in discovery's manifest");
        }
        try {
            Path checkout = checkouts.checkout(location.path("repository").asString(),
                    location.path("commitSha").asString());
            return run.compare(sha, Files.readString(discovery.resolve("blobs").resolve(sha)), checkout,
                    location.path("path").asString());
        } catch (IllegalStateException | UncheckedIOException | IOException e) {
            return ImageComparison.notBuilt(sha, "cannot check out: " + e.getMessage());
        }
    }

    private static boolean dockerRuns() {
        try {
            return !Processes.output(List.of("docker", "info", "--format", "{{.ServerVersion}}")).isBlank();
        } catch (IllegalStateException | UncheckedIOException e) {
            return false;
        }
    }

    private static String summary(ImageComparison.Result result) {
        String outcome = !result.built() ? "not built: " + result.failure()
                : result.same() ? "same" + (result.stubs().isEmpty() ? "" : ", on stubs " + result.stubs())
                : "differs: " + result.config() + " " + result.files();
        return result.blobSha().substring(0, 8) + "  " + outcome;
    }
}
