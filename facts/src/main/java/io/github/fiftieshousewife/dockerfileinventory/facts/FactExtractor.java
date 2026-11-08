package io.github.fiftieshousewife.dockerfileinventory.facts;

import io.github.fiftieshousewife.dockerfile.reader.internal.ArgPairs;
import io.github.fiftieshousewife.dockerfile.reader.internal.ArgumentText;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import io.github.fiftieshousewife.dockerfile.reader.internal.Expansions;
import io.github.fiftieshousewife.dockerfile.reader.internal.KeyValuePairs;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

/**
 * Reduces a parsed Dockerfile to {@link ImageFacts}, so the modules that read facts never see rewrite-docker's
 * unstable tree types.
 */
public final class FactExtractor {

    private FactExtractor() {
    }

    /** The facts of the image the file ships: its final stage, with what it inherits from stages it is built on. */
    public static ImageFacts extract(Docker.File file) {
        Map<String, String> globalArgs = globalArgs(file);
        Map<String, StageEnd> byStageName = new HashMap<>();
        RunTally runs = new RunTally();
        List<Docker.Stage> stages = file.getStages();
        StageEnd end = IntStream.range(0, stages.size())
                .mapToObj(index -> scanned(index, stages.get(index), globalArgs, byStageName, runs))
                .reduce(StageEnd.NONE, (earlier, later) -> later);
        return new ImageFacts(stages.size(), BaseImages.finalBase(file, globalArgs), runs.calls(), runs.runs(),
                runs.opaque(), end.user(), kind(end.user()), end.ports(), end.entrypoint(),
                List.copyOf(BuildKitFeatures.of(file)), runs.otherShell());
    }

    static StageEnd scanned(int index, Docker.Stage stage, Map<String, String> globalArgs,
            Map<String, StageEnd> byStageName, RunTally runs) {
        Docker.From from = stage.getFrom();
        StageScan scan = new StageScan(
                byStageName.getOrDefault(ArgumentText.of(from.getImageName(), globalArgs), StageEnd.NONE), globalArgs);
        boolean underOtherShell = false;
        for (Docker.Instruction instruction : stage.getInstructions()) {
            scan.accept(instruction);
            switch (instruction) {
                case Docker.Shell shell -> underOtherShell = RunScripts.otherShell(shell);
                case Docker.Run run -> runs.add(index, run, scan.variables(), underOtherShell);
                default -> {
                }
            }
        }
        StageEnd end = scan.result();
        Optional.ofNullable(from.getAs()).ifPresent(as -> byStageName.put(as.getName().getText(), end));
        return end;
    }

    static Map<String, String> globalArgs(Docker.File file) {
        Map<String, String> globalArgs = new HashMap<>();
        file.getGlobalArgs().forEach(arg -> ArgPairs.of(arg).forEach((name, value) -> {
            if (value != null) {
                globalArgs.put(name, Expansions.expanded(value, globalArgs));
            }
        }));
        return globalArgs;
    }

    static UserKind kind(String user) {
        if (user == null || user.equals("root") || user.equals("0")) {
            return UserKind.ROOT;
        }
        if (user.contains("${")) {
            return UserKind.UNRESOLVED;
        }
        return user.chars().allMatch(Character::isDigit) ? UserKind.NUMERIC : UserKind.NAMED;
    }
}
