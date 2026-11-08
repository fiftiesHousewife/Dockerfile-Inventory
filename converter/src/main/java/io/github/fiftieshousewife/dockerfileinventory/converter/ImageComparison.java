package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import tools.jackson.databind.ObjectMapper;

final class ImageComparison {

    record Result(String blobSha, boolean built, String failure, boolean same, List<String> config,
            List<String> files) {

        Result {
            config = List.copyOf(config);
            files = List.copyOf(files);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private ImageComparison() {
    }

    static Result of(Path pair) {
        String sha = String.valueOf(pair.getFileName());
        Path failure = pair.resolve("failure.txt");
        if (Files.exists(failure)) {
            return new Result(sha, false, RunFiles.read(failure).strip(), false, List.of(), List.of());
        }
        Path original = pair.resolve("original");
        Path regenerated = pair.resolve("regenerated");
        List<String> config = ConfigDifferences.of(JSON.readTree(original.resolve("config.json").toFile()),
                JSON.readTree(regenerated.resolve("config.json").toFile()));
        List<String> files = FileDifferences.of(listing(original), listing(regenerated));
        return new Result(sha, true, null, config.isEmpty() && files.isEmpty(), config, files);
    }

    private static SortedMap<String, FileListing.Entry> listing(Path image) {
        return FileListing.of(RunFiles.read(image.resolve("files.txt")), RunFiles.read(image.resolve("sums.txt")));
    }
}
