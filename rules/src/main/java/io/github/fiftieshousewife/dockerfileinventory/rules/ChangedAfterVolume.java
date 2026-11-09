package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import io.github.fiftieshousewife.dockerfile.reader.internal.KeyValuePairs;
import io.github.fiftieshousewife.dockerfileinventory.facts.PermissionChange;
import io.github.fiftieshousewife.dockerfileinventory.facts.StagePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.openrewrite.Cursor;

// Docker's legacy builder discards a RUN's changes inside a declared volume; BuildKit keeps them.
final class ChangedAfterVolume implements Rule {

    record Declared(String path, int line) {
    }

    @Override
    public String id() {
        return "H7";
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
        List<Declared> volumes = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        FinalImage.instructions(subject.file()).forEach(instruction -> {
            String line = Continuations.oneLine(instruction.printTrimmed(cursor));
            switch (instruction) {
                case Docker.Volume _ -> paths(line).forEach(path -> volumes.add(new Declared(path,
                        lines.of(instruction))));
                case Docker.Run _ -> messages(line, volumes).forEach(message ->
                        findings.add(finding(subject, lines.of(instruction), message)));
                default -> {
                }
            }
        });
        return findings;
    }

    static List<String> messages(String run, List<Declared> volumes) {
        return StagePermissions.ofRun(run).stream().flatMap(change -> changed(change, volumes)
                .map(volume -> ChangedAfterCopy.written(change) + " changes the volume " + volume.path()
                        + " declared on line " + volume.line()).stream()).toList();
    }

    static Optional<Declared> changed(PermissionChange change, List<Declared> volumes) {
        return volumes.stream().filter(volume -> change.paths().stream()
                .anyMatch(path -> ChangedAfterCopy.rewrites(path, change.recursive(), volume.path()))).findFirst();
    }

    static List<String> paths(String volume) {
        return KeyValuePairs.words(volume).stream().skip(1)
                .map(word -> KeyValuePairs.unquoted(word.replaceAll("^\\[|[],]$", "")))
                .filter(path -> !path.isEmpty()).toList();
    }
}
