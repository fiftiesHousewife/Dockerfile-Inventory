package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.regex.Pattern;
import org.openrewrite.Cursor;
import org.openrewrite.docker.tree.Docker;

/** S3: a script downloaded and run in one step, so nothing records what ran. */
public final class PipeToShell implements Rule {

    /** {@code curl} or {@code wget}, then a pipe (no other command between) into a shell, perhaps through sudo. */
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
                .filter(run -> PIPED.matcher(joined(run.printTrimmed(cursor))).find())
                .map(run -> finding(subject, lines.of(run), "a downloaded script is piped straight into a shell"))
                .toList();
    }

    /** The instruction as one line: backslash-newline continuations joined. */
    static String joined(String instruction) {
        return instruction.replaceAll("\\\\\\r?\\n", " ");
    }
}
