package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import tools.jackson.databind.JsonNode;

// One Dockerfile and the converter's rendering of it, built from the same context and compared.
final class ImageComparisonRun {

    interface Builder {

        boolean build(Path dockerfile, Path context, String tag, Path log);

        JsonNode inspect(String tag);

        SortedMap<String, FileEntry> files(String tag);
    }

    record Context(Path directory, List<String> stubs) {
    }

    private final Builder builder;
    private final Path work;
    private final String arch;

    ImageComparisonRun(Builder builder, Path work, String arch) {
        this.builder = builder;
        this.work = work;
        this.arch = arch;
    }

    ImageComparison.Result compare(String sha, String original, Path checkout, String path) {
        Path into = work.resolve(sha);
        Path regenerated = into.resolve("regenerated.Dockerfile");
        List<String> sources;
        try {
            RunFiles.writeFile(regenerated, Regenerated.of(original));
            sources = Regenerated.localSources(original);
        } catch (IllegalArgumentException e) {
            return ImageComparison.notBuilt(sha, "the converter cannot render it: " + e.getMessage());
        }
        Optional<Context> context = originalContext(sha, checkout, checkout.resolve(path), sources);
        if (context.isEmpty()) {
            return ImageComparison.notBuilt(sha, "the original does not build with its directory or the repository"
                    + " root as context, nor on stubs for its missing sources; see original-build-*.log");
        }
        if (!builder.build(regenerated, context.get().directory(), tag(sha, "regenerated"),
                into.resolve("regenerated-build.log"))) {
            return ImageComparison.notBuilt(sha, "the regenerated Dockerfile does not build where the original"
                    + " does; see regenerated-build.log");
        }
        return ImageComparison.compared(sha, builder.inspect(tag(sha, "original")),
                builder.inspect(tag(sha, "regenerated")), builder.files(tag(sha, "original")),
                builder.files(tag(sha, "regenerated")), context.get().stubs());
    }

    // The Dockerfile's directory, then the repository root, then the directory with CI-made sources stubbed.
    Optional<Context> originalContext(String sha, Path checkout, Path dockerfile, List<String> sources) {
        Path directory = dockerfile.toAbsolutePath().getParent();
        Path logs = work.resolve(sha);
        if (builder.build(dockerfile, directory, tag(sha, "original"), logs.resolve("original-build-directory.log"))) {
            return Optional.of(new Context(directory, List.of()));
        }
        if (builder.build(dockerfile, checkout, tag(sha, "original"), logs.resolve("original-build-root.log"))) {
            return Optional.of(new Context(checkout, List.of()));
        }
        List<String> stubs = Stubs.create(directory, sources, arch);
        return !stubs.isEmpty() && builder.build(dockerfile, directory, tag(sha, "original"),
                logs.resolve("original-build-stubbed.log"))
                ? Optional.of(new Context(directory, stubs)) : Optional.empty();
    }

    private static String tag(String sha, String which) {
        return "compare/" + sha + ":" + which;
    }
}
