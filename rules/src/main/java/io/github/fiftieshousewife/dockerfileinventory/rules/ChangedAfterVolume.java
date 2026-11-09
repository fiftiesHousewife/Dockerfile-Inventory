package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import io.github.fiftieshousewife.dockerfile.reader.internal.KeyValuePairs;
import io.github.fiftieshousewife.dockerfileinventory.facts.PermissionChange;
import io.github.fiftieshousewife.dockerfileinventory.facts.StagePermissions;
import java.util.ArrayList;
import java.util.List;
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
        for (Docker.Instruction instruction : FinalImage.instructions(subject.file())) {
            String line = Continuations.oneLine(instruction.printTrimmed(cursor));
            if (instruction instanceof Docker.Volume) {
                paths(line).forEach(path -> volumes.add(new Declared(path, lines.of(instruction))));
            } else if (instruction instanceof Docker.Run) {
                for (PermissionChange change : StagePermissions.ofRun(line)) {
                    volumes.stream().filter(volume -> change.paths().stream().anyMatch(path ->
                                    ChangedAfterCopy.rewrites(path, change.recursive(), volume.path())))
                            .findFirst().ifPresent(volume -> findings.add(finding(subject, lines.of(instruction),
                                    ChangedAfterCopy.written(change) + " changes the volume " + volume.path()
                                            + " declared on line " + volume.line())));
                }
            }
        }
        return findings;
    }

    static List<String> paths(String volume) {
        return KeyValuePairs.words(volume).stream().skip(1)
                .map(word -> KeyValuePairs.unquoted(word.replaceAll("^\\[|[],]$", "")))
                .filter(path -> !path.isEmpty()).toList();
    }
}
