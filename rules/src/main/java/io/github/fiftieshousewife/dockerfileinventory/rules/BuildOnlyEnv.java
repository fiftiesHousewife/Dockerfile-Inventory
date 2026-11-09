package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import io.github.fiftieshousewife.dockerfile.reader.internal.KeyValuePairs;
import java.util.List;
import java.util.Set;
import org.openrewrite.Cursor;

// Variables only a build needs, which an ENV leaves set in every container (Docker's FAQ on DEBIAN_FRONTEND).
final class BuildOnlyEnv implements Rule {

    private static final Set<String> BUILD_ONLY = Set.of("DEBIAN_FRONTEND");

    @Override
    public String id() {
        return "H9";
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
        return FinalImage.instructions(subject.file()).stream()
                .filter(i -> i instanceof Docker.Env)
                .flatMap(env -> names(Continuations.oneLine(env.printTrimmed(cursor))).stream()
                        .filter(BUILD_ONLY::contains)
                        .map(name -> finding(subject, lines.of(env), "ENV " + name
                                + ", a setting for the build, stays set in every container")))
                .toList();
    }

    // The names an ENV sets: NAME=value pairs, or the older NAME value form.
    static List<String> names(String env) {
        String text = env.split("\\s+", 2).length > 1 ? env.split("\\s+", 2)[1].strip() : "";
        String first = text.split("\\s+", 2)[0];
        return first.contains("=") ? KeyValuePairs.keys(text) : List.of(first);
    }
}
