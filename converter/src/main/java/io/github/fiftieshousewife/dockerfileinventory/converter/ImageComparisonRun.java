package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import tools.jackson.databind.JsonNode;

final class ImageComparisonRun {

    interface Builder {

        BuildOutcome build(Path dockerfile, Path context, String tag, Path log, List<String> emptySecretIds);

        JsonNode inspect(String tag);

        SortedMap<String, FileEntry> files(String tag);

        void remove(List<String> tags);
    }

    record Context(Path directory, List<String> stubs) {
    }

    record Inputs(List<String> sources, List<String> secrets) {
    }

    sealed interface Original {

        record Found(Context context) implements Original {
        }

        record Refused(String failure) implements Original {
        }
    }

    private static final String NEVER_BUILDS = "the original does not build with its directory or the repository"
            + " root as context, nor on stubs for its missing sources; see original-build-*.log";

    private final Builder builder;
    private final Path work;
    private final String arch;

    ImageComparisonRun(Builder builder, Path work, String arch) {
        this.builder = builder;
        this.work = work;
        this.arch = arch;
    }

    ImageComparison.Result compare(String sha, String original, Path checkout, String path) {
        try {
            return buildAndCompare(sha, original, checkout, path);
        } finally {
            builder.remove(List.of(tag(sha, "original"), tag(sha, "regenerated"), tag(sha, "control")));
        }
    }

    ImageComparison.Result buildAndCompare(String sha, String original, Path checkout, String path) {
        Path regenerated = work.resolve(sha).resolve("regenerated.Dockerfile");
        Inputs inputs;
        try {
            RunFiles.writeFile(regenerated, Regenerated.of(original));
            inputs = new Inputs(Regenerated.localSources(original), Regenerated.secretIds(original));
        } catch (IllegalArgumentException e) {
            return ImageComparison.notBuilt(sha, "the converter cannot render it: " + e.getMessage());
        }
        return switch (originalContext(sha, checkout, checkout.resolve(path), inputs)) {
            case Original.Refused refused -> ImageComparison.notBuilt(sha, refused.failure());
            case Original.Found found -> regeneratedAndCompared(sha, checkout.resolve(path), regenerated,
                    found.context(), inputs);
        };
    }

    ImageComparison.Result regeneratedAndCompared(String sha, Path dockerfile, Path regenerated, Context context,
            Inputs inputs) {
        BuildOutcome outcome;
        boolean controlled;
        try (ExecutorService together = Executors.newVirtualThreadPerTaskExecutor()) {
            CompletableFuture<BuildOutcome> control = CompletableFuture.supplyAsync(() -> builder.build(dockerfile,
                    context.directory(), tag(sha, "control"), work.resolve(sha).resolve("control-build.log"),
                    inputs.secrets()), together);
            outcome = builder.build(regenerated, context.directory(), tag(sha, "regenerated"),
                    work.resolve(sha).resolve("regenerated-build.log"), inputs.secrets());
            controlled = control.join() == BuildOutcome.BUILT;
        }
        if (outcome != BuildOutcome.BUILT) {
            return ImageComparison.notBuilt(sha, outcome == BuildOutcome.TIMED_OUT
                    ? "the regenerated Dockerfile took longer than BUILD_TIMEOUT; see regenerated-build.log"
                    : "the regenerated Dockerfile does not build where the original does; see regenerated-build.log");
        }
        return compared(sha, context.stubs(), controlled);
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

    Original originalContext(String sha, Path checkout, Path dockerfile, Inputs inputs) {
        Path directory = dockerfile.toAbsolutePath().getParent();
        BuildOutcome fromDirectory = original(sha, dockerfile, directory, "directory", inputs);
        if (fromDirectory != BuildOutcome.MISSING_SOURCE) {
            return outcomeOf(fromDirectory, new Context(directory, List.of()), "directory");
        }
        BuildOutcome fromRoot = original(sha, dockerfile, checkout, "root", inputs);
        if (fromRoot != BuildOutcome.MISSING_SOURCE) {
            return outcomeOf(fromRoot, new Context(checkout, List.of()), "root");
        }
        List<String> stubs = Stubs.create(directory, inputs.sources(), arch);
        if (stubs.isEmpty()) {
            return new Original.Refused(NEVER_BUILDS);
        }
        BuildOutcome onStubs = original(sha, dockerfile, directory, "stubbed", inputs);
        return onStubs == BuildOutcome.MISSING_SOURCE ? new Original.Refused(NEVER_BUILDS)
                : outcomeOf(onStubs, new Context(directory, stubs), "stubbed");
    }

    static Original outcomeOf(BuildOutcome outcome, Context context, String attempt) {
        return switch (outcome) {
            case BUILT -> new Original.Found(context);
            case TIMED_OUT -> new Original.Refused(
                    "the original took longer than BUILD_TIMEOUT; see original-build-" + attempt + ".log");
            case MISSING_SOURCE, FAILED -> new Original.Refused(
                    "the original does not build; see original-build-" + attempt + ".log");
        };
    }

    private BuildOutcome original(String sha, Path dockerfile, Path context, String attempt, Inputs inputs) {
        return builder.build(dockerfile, context, tag(sha, "original"),
                work.resolve(sha).resolve("original-build-" + attempt + ".log"), inputs.secrets());
    }

    private static String tag(String sha, String which) {
        return "compare/" + sha + ":" + which;
    }
}
