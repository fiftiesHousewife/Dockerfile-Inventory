package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.Continuations;
import java.util.List;
import java.util.regex.Pattern;
import org.openrewrite.Cursor;
import org.openrewrite.docker.tree.Docker;

final class PipeToShell implements Rule {

    private static final Pattern PIPED =
            Pattern.compile("\\b(curl|wget)\\b[^|;&]*\\|\\s*(sudo\\s+(-\\S+\\s+)*)?(ba|da|z|k)?sh\\b");

    @Override
    public String id() {
        return "S3";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Finding> check(Subject subject) {
        if (!subject.isParsed()) {
            return List.of();
        }
        Lines lines = Lines.of(subject.file());
        Cursor cursor = new Cursor(null, subject.file());
        return subject.file().getStages().stream()
                .flatMap(stage -> stage.getInstructions().stream())
                .filter(i -> i instanceof Docker.Run)
                .filter(run -> piped(run.printTrimmed(cursor)))
                .map(run -> finding(subject, lines.of(run), "a downloaded script is piped straight into a shell"))
                .toList();
    }

    static boolean piped(String run) {
        return PIPED.matcher(Continuations.removed(run)).find();
    }
}
