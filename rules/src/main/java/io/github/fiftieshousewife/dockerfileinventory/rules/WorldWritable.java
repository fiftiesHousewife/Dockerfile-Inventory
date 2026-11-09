package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfileinventory.facts.StagePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openrewrite.Cursor;

final class WorldWritable implements Rule {

    private static final Pattern OCTAL = Pattern.compile("[0-7]{3,4}");
    private static final Pattern CLAUSE = Pattern.compile("([ugoa]*)([-+=])([rwxXst]*)");

    @Override
    public String id() {
        return "S5";
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
        return FinalImage.instructions(subject.file()).stream()
                .filter(i -> i instanceof Docker.Run)
                .flatMap(run -> StagePermissions.ofRun(run.printTrimmed(cursor)).stream()
                        .filter(change -> change.command().equals("chmod") && everyoneWrites(change.setting()))
                        .map(change -> finding(subject, lines.of(run), "chmod " + change.setting() + " lets "
                                + "everyone write " + String.join(" ", change.paths()))))
                .toList();
    }

    static boolean everyoneWrites(String mode) {
        if (OCTAL.matcher(mode).matches()) {
            return (mode.charAt(mode.length() - 1) - '0' & 2) != 0;
        }
        return Arrays.stream(mode.split(",")).map(CLAUSE::matcher).filter(Matcher::matches)
                .anyMatch(m -> (m.group(1).contains("o") || m.group(1).contains("a")) && !m.group(2).equals("-")
                        && m.group(3).contains("w"));
    }
}
