package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Secrets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.openrewrite.docker.tree.Docker;

/** S2: a secret-shaped {@code ENV} or {@code ARG} value baked into the image. Only a hash and the line are kept. */
public final class SecretValue implements Rule {

    @Override
    public String id() {
        return "S2";
    }

    @Override
    public Severity severity() {
        return Severity.CRITICAL;
    }

    @Override
    public List<Finding> check(Subject subject) {
        if (!subject.isParsed()) {
            return List.of();
        }
        Lines lines = Lines.of(subject.file());
        List<Finding> findings = new ArrayList<>();
        List<Docker.Instruction> instructions = new ArrayList<>(subject.file().getGlobalArgs());
        subject.file().getStages().forEach(stage -> instructions.addAll(stage.getInstructions()));
        for (Docker.Instruction instruction : instructions) {
            switch (instruction) {
                case Docker.Env env -> env.getPairs().forEach(pair -> check(subject, lines.of(env), "ENV",
                        pair.getKey().getText(), pair.getValue()).ifPresent(findings::add));
                case Docker.Arg arg -> check(subject, lines.of(arg), "ARG", arg.getName().getText(), arg.getValue())
                        .ifPresent(findings::add);
                default -> {
                }
            }
        }
        return findings;
    }

    private Optional<Finding> check(Subject subject, int line, String keyword, String name, Docker.Argument value) {
        Optional<String> literal = literal(value);
        if (literal.isEmpty() || !secretName(name) && !secretShape(literal.get())) {
            return Optional.empty();
        }
        return Optional.of(new Finding(id(), severity(), subject.blobSha(), line,
                keyword + " " + name + " has a secret-shaped value", Hashes.sha256(literal.get()), List.of()));
    }

    /** As {@link Secrets#secretName}; kept here so S2's tests read as before. */
    static boolean secretName(String name) {
        return Secrets.secretName(name);
    }

    static boolean secretShape(String value) {
        return Secrets.secretShape(value);
    }

    /**
     * The value when it is written out in full: no variable in it, and not empty once quotes are removed.
     * rewrite-docker 8.90.4 leaves {@code $NAME} inside an {@code ENV} value as plain text, so a {@code $} outside
     * single quotes counts as a variable; and it gives an empty {@code KEY=} the value {@code =}.
     */
    static Optional<String> literal(Docker.Argument value) {
        if (value == null || !value.getContents().stream().allMatch(c -> c instanceof Docker.Literal)) {
            return Optional.empty();
        }
        String text = value.getContents().stream()
                .map(c -> ((Docker.Literal) c).getText())
                .collect(Collectors.joining())
                .strip();
        boolean singleQuoted = text.length() >= 2 && text.startsWith("'") && text.endsWith("'");
        if (!singleQuoted && text.contains("$") || text.equals("=")) {
            return Optional.empty();
        }
        if (singleQuoted || text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }
}
