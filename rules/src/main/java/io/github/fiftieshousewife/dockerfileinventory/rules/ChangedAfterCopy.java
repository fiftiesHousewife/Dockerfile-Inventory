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
        FinalImage.instructions(subject.file()).forEach(instruction -> {
            String line = Continuations.oneLine(instruction.printTrimmed(cursor));
            if (instruction instanceof Docker.Copy || instruction instanceof Docker.Add) {
                copied(line, lines.of(instruction)).ifPresent(copied::add);
            } else if (instruction instanceof Docker.Run) {
                messages(line, copied).forEach(message ->
                        findings.add(finding(subject, lines.of(instruction), message)));
            }
        });
        return findings;
    }

    static Optional<Copied> copied(String line, int lineNumber) {
        String destination = AddSources.of(line).destination();
        return destination != null && destination.startsWith("/") && !destination.contains("$")
                ? Optional.of(new Copied(destination, line.split("\\s+", 2)[0].toUpperCase(Locale.ROOT),
                        lineNumber))
                : Optional.empty();
    }

    static List<String> messages(String run, List<Copied> copied) {
        return StagePermissions.ofRun(run).stream().flatMap(change -> rewritten(change, copied)
                .map(copy -> written(change) + " rewrites what " + copy.instruction() + " wrote to "
                        + copy.destination() + " on line " + copy.line()).stream()).toList();
    }

    static Optional<Copied> rewritten(PermissionChange change, List<Copied> copied) {
        return copied.stream().filter(copy -> change.paths().stream()
                .anyMatch(path -> rewrites(path, change.recursive(), copy.destination()))).findFirst();
    }

    static boolean rewrites(String path, boolean recursive, String destination) {
        String changed = TrailingSlash.removed(path);
        String copy = TrailingSlash.removed(destination);
        return changed.equals(copy) || recursive && copy.startsWith(changed + "/") || changed.startsWith(copy + "/");
    }

    static String written(PermissionChange change) {
        return change.command() + (change.recursive() ? " -R " : " ") + change.setting() + " "
                + String.join(" ", change.paths());
    }
}
