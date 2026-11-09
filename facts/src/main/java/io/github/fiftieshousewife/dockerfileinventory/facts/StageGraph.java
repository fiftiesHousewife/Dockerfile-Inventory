package io.github.fiftieshousewife.dockerfileinventory.facts;

import io.github.fiftieshousewife.dockerfile.reader.internal.ArgumentText;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import io.github.fiftieshousewife.dockerfile.reader.internal.ShellTokeniser;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.openrewrite.Cursor;

final class StageGraph {

    private StageGraph() {
    }

    static List<ImageStage> of(Docker.File file) {
        Map<String, String> globalArgs = FactExtractor.globalArgs(file);
        Set<String> earlier = new HashSet<>();
        List<ImageStage> stages = new ArrayList<>();
        for (Docker.Stage stage : file.getStages()) {
            Docker.From from = stage.getFrom();
            String image = ArgumentText.of(from.getImageName(), globalArgs);
            boolean onStage = from.getTag() == null && from.getDigest() == null && earlier.contains(image);
            Docker.From.As as = from.getAs();
            String name = as == null ? null : as.getName().getText();
            stages.add(scanned(file, stage, Set.copyOf(earlier)).stage(name,
                    onStage ? null : BaseImages.base(from, globalArgs), onStage ? image : null));
            if (name != null) {
                earlier.add(name);
            }
        }
        return stages;
    }

    static Scan scanned(Docker.File file, Docker.Stage stage, Set<String> earlier) {
        Scan scan = new Scan(earlier);
        stage.getInstructions().stream()
                .map(instruction -> Continuations.oneLine(instruction.printTrimmed(new Cursor(null, file))))
                .forEach(scan::accept);
        return scan;
    }

    static final class Scan {

        private final Set<String> earlier;
        private final List<CopyFact> copies = new ArrayList<>();
        private final List<PermissionChange> permissions = new ArrayList<>();
        private final List<String> unusual = new ArrayList<>();
        private int instructions;
        private int runs;
        private int commands;
        private int unreadRuns;
        private int layers;

        Scan(Set<String> earlier) {
            this.earlier = earlier;
        }

        void accept(String line) {
            instructions++;
            UnusualSteps.of(line).ifPresent(unusual::add);
            String keyword = line.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
            switch (keyword) {
                case "RUN" -> run(scriptAfterFlags(line));
                case "COPY", "ADD" -> copies.add(StageCopies.copy(line, earlier));
                default -> {
                }
            }
            if (keyword.equals("RUN") || keyword.equals("COPY") || keyword.equals("ADD")) {
                layers++;
            }
        }

        void run(String script) {
            runs++;
            if (script.startsWith("[")) {
                commands++;
                return;
            }
            Optional<List<List<String>>> read = ShellTokeniser.parse(script);
            if (read.isEmpty()) {
                unreadRuns++;
                return;
            }
            commands += read.get().size();
            permissions.addAll(StagePermissions.of(script));
        }

        ImageStage stage(String name, BaseImage base, String fromStage) {
            return new ImageStage(name, base, fromStage, copies.stream().filter(c -> c.from() != null)
                    .map(CopyFact::from).distinct().toList(), instructions, runs, commands, unreadRuns, layers,
                    copies, permissions, unusual);
        }

        static String scriptAfterFlags(String line) {
            String rest = line.split("\\s+", 2).length > 1 ? line.split("\\s+", 2)[1] : "";
            return rest.replaceFirst("^(--\\S+\\s+)*", "").strip();
        }
    }
}
