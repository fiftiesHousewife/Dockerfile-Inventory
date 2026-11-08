package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import tools.jackson.databind.JsonNode;

final class ImageComparisonRun {

    interface Builder {

        // Each secret id is given an empty secret.
        boolean build(Path dockerfile, Path context, String tag, Path log, List<String> secrets);

        JsonNode inspect(String tag);

        SortedMap<String, FileEntry> files(String tag);
    }

    record Context(Path directory, List<String> stubs) {
    }

    record Inputs(List<String> sources, List<String> secrets) {
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
        Inputs inputs;
        try {
            RunFiles.writeFile(regenerated, Regenerated.of(original));
            inputs = new Inputs(Regenerated.localSources(original), Regenerated.secretIds(original));
        } catch (IllegalArgumentException e) {
            return ImageComparison.notBuilt(sha, "the converter cannot render it: " + e.getMessage());
        }
        Optional<Context> context = originalContext(sha, checkout, checkout.resolve(path), inputs);
        if (context.isEmpty()) {
            return ImageComparison.notBuilt(sha, "the original does not build with its directory or the repository"
                    + " root as context, nor on stubs for its missing sources; see original-build-*.log");
        }
        if (!builder.build(regenerated, context.get().directory(), tag(sha, "regenerated"),
                into.resolve("regenerated-build.log"), inputs.secrets())) {
            return ImageComparison.notBuilt(sha, "the regenerated Dockerfile does not build where the original"
                    + " does; see regenerated-build.log");
        }
        boolean controlled = builder.build(checkout.resolve(path), context.get().directory(), tag(sha, "control"),
                into.resolve("control-build.log"), inputs.secrets());
        return compared(sha, context.get().stubs(), controlled);
    }

    ImageComparison.Result compared(String sha, List<String> stubs, boolean controlled) {
        try {
            Optional<ImageComparison.Control> control = controlled ? Optional.of(new ImageComparison.Control(
                    builder.inspect(tag(sha, "control")), builder.files(tag(sha, "control")))) : Optional.empty();
            return ImageComparison.compared(sha, builder.inspect(tag(sha, "original")),
                    builder.inspect(tag(sha, "regenerated")), builder.files(tag(sha, "original")),
                    builder.files(tag(sha, "regenerated")), stubs, control);
        } catch (IllegalStateException | UncheckedIOException e) {
            return ImageComparison.notBuilt(sha, "the images could not be read: " + e.getMessage());
        }
    }

    Optional<Context> originalContext(String sha, Path checkout, Path dockerfile, Inputs inputs) {
        Path directory = dockerfile.toAbsolutePath().getParent();
        if (original(sha, dockerfile, directory, "directory", inputs)) {
            return Optional.of(new Context(directory, List.of()));
        }
        if (original(sha, dockerfile, checkout, "root", inputs)) {
            return Optional.of(new Context(checkout, List.of()));
        }
        List<String> stubs = Stubs.create(directory, inputs.sources(), arch);
        return !stubs.isEmpty() && original(sha, dockerfile, directory, "stubbed", inputs)
                ? Optional.of(new Context(directory, stubs)) : Optional.empty();
    }

    private boolean original(String sha, Path dockerfile, Path context, String attempt, Inputs inputs) {
        return builder.build(dockerfile, context, tag(sha, "original"),
                work.resolve(sha).resolve("original-build-" + attempt + ".log"), inputs.secrets());
    }

    private static String tag(String sha, String which) {
        return "compare/" + sha + ":" + which;
    }
}
