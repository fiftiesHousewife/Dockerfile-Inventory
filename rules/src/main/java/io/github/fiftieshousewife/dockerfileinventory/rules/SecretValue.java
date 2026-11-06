package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.openrewrite.docker.tree.Docker;

/** S2: a secret-shaped {@code ENV} or {@code ARG} value baked into the image. Only a hash and the line are kept. */
public final class SecretValue implements Rule {

    private static final List<String> SECRET_WORDS = List.of("PASSWORD", "PASSWD", "SECRET", "TOKEN", "CREDENTIAL");
    private static final List<Pattern> SECRET_SHAPES = List.of(
            Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));

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

    /**
     * Names are matched by their parts (split on {@code _}, {@code -} and {@code .}): {@code KEY} only as a whole part,
     * so {@code VALKEY_PREFIX} and {@code KEYCLOAK_URL} do not count; the longer words anywhere in a part, so
     * {@code DBPASSWORD} does.
     */
    static boolean secretName(String name) {
        List<String> parts = List.of(name.toUpperCase(Locale.ROOT).split("[_.\\-]+"));
        return parts.contains("KEY")
                || parts.stream().anyMatch(part -> SECRET_WORDS.stream().anyMatch(part::contains));
    }

    /** Values that are secrets whatever they are called: a private key, an AWS access key ID, a JWT. */
    static boolean secretShape(String value) {
        return SECRET_SHAPES.stream().anyMatch(shape -> shape.matcher(value).find());
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
