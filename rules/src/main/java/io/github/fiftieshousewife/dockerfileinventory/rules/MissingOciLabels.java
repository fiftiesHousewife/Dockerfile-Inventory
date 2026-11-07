package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        return FinalImage.instructions(file).stream()
                .flatMap(i -> i instanceof Docker.Label label ? label.getPairs().stream() : Stream.empty())
                .map(pair -> key(pair.getKey()))
                .collect(Collectors.toSet());
    }

    static String key(Docker.Argument key) {
        return key.getContents().stream()
                .map(c -> c instanceof Docker.Literal literal ? literal.getText() : "")
                .collect(Collectors.joining());
    }
}
