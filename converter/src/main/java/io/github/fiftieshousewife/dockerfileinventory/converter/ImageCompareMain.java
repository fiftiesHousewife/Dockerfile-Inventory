package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

/** The Java half of {@code scripts/compare-images.sh}: renders the regenerated Dockerfiles, then compares images. */
public final class ImageCompareMain {

    record Line(String blobSha, boolean built, String failure, boolean same, List<String> config, List<String> files,
            List<String> ignoredContent) {
    }

    enum Step { RENDER, COMPARE }

    private static final ObjectMapper JSON = new ObjectMapper();

    private ImageCompareMain() {
    }

    /** Arguments are ignored: the script passes its settings in the environment. */
    public static void main(String[] args) {
        Path work = Path.of(ConverterMain.required("WORK_DIR", System.getenv("WORK_DIR")));
        if (step(System.getenv("STEP")) == Step.RENDER) {
            render(Path.of(ConverterMain.required("DISCOVERY_DIR", System.getenv("DISCOVERY_DIR"))),
                    RunFiles.read(Path.of(ConverterMain.required("SHAS", System.getenv("SHAS")))).lines()
                            .map(String::strip).filter(line -> !line.isEmpty()).toList(),
                    work);
        } else {
            write(work.resolve("comparison.jsonl"), compare(work));
        }
    }

    static Step step(String value) {
        return switch (value == null ? "" : value) {
            case "render" -> Step.RENDER;
            case "compare" -> Step.COMPARE;
            default -> throw new IllegalStateException("STEP is render or compare, not " + value);
        };
    }

    static void render(Path discovery, List<String> shas, Path work) {
        shas.forEach(sha -> {
            String original = RunFiles.read(discovery.resolve("blobs").resolve(sha));
            try {
                write(work.resolve(sha).resolve("regenerated.Dockerfile"), Regenerated.of(original));
            } catch (IllegalArgumentException e) {
                write(work.resolve(sha).resolve("failure.txt"), "the converter cannot render it: " + e.getMessage());
            }
        });
    }

    static String compare(Path work) {
        List<String> ignored = Stream.concat(FileDifferences.VOLATILE_DIRECTORIES.stream(),
                FileDifferences.VOLATILE_FILES.stream()).toList();
        try (Stream<Path> pairs = Files.list(work)) {
            return pairs.filter(Files::isDirectory).sorted().map(ImageComparison::of)
                    .map(r -> new Line(r.blobSha(), r.built(), r.failure(), r.same(), r.config(), r.files(), ignored))
                    .map(JSON::writeValueAsString)
                    .collect(Collectors.joining("\n", "", "\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String text) {
        try {
            Path directory = file.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
