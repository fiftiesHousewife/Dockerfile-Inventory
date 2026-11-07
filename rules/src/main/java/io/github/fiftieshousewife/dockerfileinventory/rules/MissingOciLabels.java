package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.Continuations;
import io.github.fiftieshousewife.dockerfileinventory.facts.KeyValuePairs;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.openrewrite.Cursor;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class MissingOciLabels implements Rule {

    private static final List<String> REQUIRED =
            List.of("org.opencontainers.image.revision", "org.opencontainers.image.source");

    @Override
    public String id() {
        return "R3";
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
        Set<String> labels = labels(subject.file());
        List<String> missing = REQUIRED.stream().filter(label -> !labels.contains(label)).toList();
        return missing.isEmpty() ? List.of()
                : List.of(finding(subject, null, "the image has no " + String.join(" or ", missing) + " label"));
    }

    static Set<String> labels(Docker.File file) {
        Cursor cursor = new Cursor(null, file);
        return FinalImage.instructions(file).stream()
                .filter(i -> i instanceof Docker.Label)
                .map(i -> Continuations.removed(Lines.withoutLeadingComments(i.printTrimmed(cursor))).strip())
                .flatMap(text -> KeyValuePairs.keys(text.substring(text.indexOf(' ') + 1)).stream())
                .collect(Collectors.toSet());
    }
}
