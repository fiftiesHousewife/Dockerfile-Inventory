package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class ImageComparison {

    record Result(String blobSha, boolean built, String failure, boolean same, List<String> config,
            List<String> files, List<String> stubs) {

        Result {
            config = List.copyOf(config);
            files = List.copyOf(files);
            stubs = List.copyOf(stubs);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private ImageComparison() {
    }

    static Result of(Path pair) {
        String sha = String.valueOf(pair.getFileName());
        Path failure = pair.resolve("failure.txt");
        if (Files.exists(failure)) {
            return new Result(sha, false, RunFiles.read(failure).strip(), false, List.of(), List.of(), List.of());
        }
        Path original = pair.resolve("original");
        Path regenerated = pair.resolve("regenerated");
        List<String> config = ConfigDifferences.of(config(original), config(regenerated));
        List<String> files = FileDifferences.of(listing(original), listing(regenerated));
        Path stubbed = pair.resolve("stubs.txt");
        List<String> stubs = Files.exists(stubbed)
                ? RunFiles.read(stubbed).lines().filter(line -> !line.isBlank()).toList() : List.of();
        return new Result(sha, true, null, config.isEmpty() && files.isEmpty(), config, files, stubs);
    }

    // Docker's config keeps HEALTHCHECK, ONBUILD and SHELL, which the OCI one has no place for.
    static JsonNode config(Path image) {
        JsonNode inspected = JSON.readTree(image.resolve("inspect.json").toFile());
        JsonNode docker = inspected.path("Docker").path("config");
        return docker.isMissingNode() ? inspected.path("OCIv1").path("config") : docker;
    }

    private static SortedMap<String, FileListing.Entry> listing(Path image) {
        return FileListing.of(RunFiles.read(image.resolve("files.txt")), RunFiles.read(image.resolve("sums.txt")));
    }
}
