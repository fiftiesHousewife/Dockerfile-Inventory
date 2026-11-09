package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.openrewrite.Cursor;

final class PlainAdd implements Rule {

    // ADD unpacks a local tar archive, plain or compressed with gzip, bzip2, xz or zstd.
    private static final Pattern ARCHIVE =
            Pattern.compile(".*\\.(tar|tar\\.(gz|bz2|xz|zst)|tgz|tbz2?|txz|tzst)");

    @Override
    public String id() {
        return "H5";
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
        return subject.file().getStages().stream().flatMap(stage -> stage.getInstructions().stream())
                .filter(i -> i instanceof Docker.Add)
                .map(add -> Map.entry(add, AddSources.of(Continuations.oneLine(add.printTrimmed(cursor))).sources()))
                .filter(read -> plain(read.getValue()))
                .map(read -> finding(subject, lines.of(read.getKey()), "ADD of " + String.join(" ", read.getValue())
                        + ", which COPY would do"))
                .toList();
    }

    // Every source local, known (no variable) and no archive to unpack.
    static boolean plain(List<String> sources) {
        return !sources.isEmpty() && sources.stream().noneMatch(source -> AddSources.remote(source)
                || source.contains("$") || source.startsWith("<<") || archive(source));
    }

    static boolean archive(String source) {
        return ARCHIVE.matcher(source.toLowerCase(Locale.ROOT)).matches();
    }
}
