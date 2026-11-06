package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.Continuations;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.openrewrite.Cursor;
import org.openrewrite.docker.tree.Docker;

/** R3: the image does not say which commit and repository it was built from. */
public final class MissingOciLabels implements Rule {

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
        Cursor cursor = new Cursor(null, subject.file());
        Set<String> labels = FinalImage.instructions(subject.file()).stream()
                .filter(i -> i instanceof Docker.Label)
                .map(i -> Continuations.removed(Lines.withoutLeadingComments(i.printTrimmed(cursor))).strip())
                .flatMap(text -> Pairs.keys(text.substring(text.indexOf(' ') + 1)).stream())
                .collect(Collectors.toSet());
        List<String> missing = REQUIRED.stream().filter(label -> !labels.contains(label)).toList();
        return missing.isEmpty() ? List.of()
                : List.of(finding(subject, null, "the image has no " + String.join(" or ", missing) + " label"));
    }
}
