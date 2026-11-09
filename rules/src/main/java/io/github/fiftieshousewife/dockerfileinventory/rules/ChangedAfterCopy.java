package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import io.github.fiftieshousewife.dockerfileinventory.facts.PermissionChange;
import io.github.fiftieshousewife.dockerfileinventory.facts.StagePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.openrewrite.Cursor;

final class ChangedAfterCopy implements Rule {

    record Copied(String destination, String instruction, int line) {
    }

    @Override
    public String id() {
        return "H6";
    }

    @Override
    public Severity severity() {
        return Severity.LOW;
    }

    @Override
    public List<Finding> check(Subject subject) {
        if (!subject.isParsed()) {
            return List.of();
        }
        Lines lines = Lines.of(subject.file());
        Cursor cursor = new Cursor(null, subject.file());
        List<Copied> copied = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        for (Docker.Instruction instruction : FinalImage.instructions(subject.file())) {
            String line = Continuations.oneLine(instruction.printTrimmed(cursor));
            if (instruction instanceof Docker.Copy || instruction instanceof Docker.Add) {
                String destination = AddSources.of(line).destination();
                if (destination != null && destination.startsWith("/") && !destination.contains("$")) {
                    copied.add(new Copied(destination, line.split("\\s+", 2)[0].toUpperCase(Locale.ROOT),
                            lines.of(instruction)));
                }
            } else if (instruction instanceof Docker.Run) {
                StagePermissions.ofRun(line).forEach(change -> rewritten(change, copied).ifPresent(copy ->
                        findings.add(finding(subject, lines.of(instruction), written(change) + " rewrites what "
                                + copy.instruction() + " wrote to " + copy.destination() + " on line "
                                + copy.line()))));
            }
        }
        return findings;
    }

    static Optional<Copied> rewritten(PermissionChange change, List<Copied> copied) {
        return copied.stream().filter(copy -> change.paths().stream()
                .anyMatch(path -> rewrites(path, change.recursive(), copy.destination()))).findFirst();
    }

    static boolean rewrites(String path, boolean recursive, String destination) {
        String changed = trimmed(path);
        String copy = trimmed(destination);
        return changed.equals(copy) || recursive && copy.startsWith(changed + "/") || changed.startsWith(copy + "/");
    }

    private static String written(PermissionChange change) {
        return change.command() + (change.recursive() ? " -R " : " ") + change.setting() + " "
                + String.join(" ", change.paths());
    }

    private static String trimmed(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
