package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import io.github.fiftieshousewife.dockerfile.reader.internal.KeyValuePairs;
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

    // The remote sources of an ADD written without --checksum.
    static List<String> unverified(String add) {
        List<String> words = KeyValuePairs.words(add);
        List<String> flags = words.subList(1, words.size()).stream().takeWhile(w -> w.startsWith("--")).toList();
        if (flags.stream().anyMatch(flag -> flag.startsWith("--checksum="))) {
            return List.of();
        }
        List<String> written = words.subList(1 + flags.size(), words.size()).stream()
                .map(word -> KeyValuePairs.unquoted(word.replaceAll("^\\[|[],]$", ""))).toList();
        return written.isEmpty() ? List.of() : written.subList(0, written.size() - 1).stream()
                .filter(source -> source.startsWith("http://") || source.startsWith("https://")
                        || source.startsWith("git@"))
                .toList();
    }
}
