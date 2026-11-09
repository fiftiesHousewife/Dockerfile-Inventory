package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import java.util.List;
import org.openrewrite.Cursor;

final class UnverifiedDownload implements Rule {

    @Override
    public String id() {
        return "S6";
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public List<Finding> check(Subject subject) {
        if (!subject.isParsed()) {
            return List.of();
        }
        Lines lines = Lines.of(subject.file());
        Cursor cursor = new Cursor(null, subject.file());
        return subject.file().getStages().stream().flatMap(stage -> stage.getInstructions().stream())
                .filter(i -> i instanceof Docker.Add)
                .flatMap(add -> unverified(Continuations.oneLine(add.printTrimmed(cursor))).stream()
                        .map(url -> finding(subject, lines.of(add), "ADD downloads " + url + " without a checksum")))
                .toList();
    }

    static List<String> unverified(String add) {
        AddSources read = AddSources.of(add);
        return read.flags().stream().anyMatch(flag -> flag.startsWith("--checksum=")) ? List.of()
                : read.sources().stream().filter(AddSources::remote).toList();
    }
}
