package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.secrets.Secrets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class SecretValue implements Rule {

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
        return instructions(subject.file()).stream()
                .flatMap(instruction -> findings(subject, lines.of(instruction), instruction).stream())
                .toList();
    }

    static List<Docker.Instruction> instructions(Docker.File file) {
        List<Docker.Instruction> instructions = new ArrayList<>(file.getGlobalArgs());
        file.getStages().forEach(stage -> instructions.addAll(stage.getInstructions()));
        return instructions;
    }

    List<Finding> findings(Subject subject, int line, Docker.Instruction instruction) {
        return switch (instruction) {
            case Docker.Env env -> env.getPairs().stream()
                    .flatMap(pair -> check(subject, line, "ENV", pair.getKey().getText(), pair.getValue()).stream())
                    .toList();
            case Docker.Arg arg -> Stream.concat(
                            check(subject, line, "ARG", arg.getName().getText(), arg.getValue()).stream(),
                            arg.getAdditionalPairs().stream().flatMap(pair ->
                                    check(subject, line, "ARG", pair.getName().getText(), pair.getValue()).stream()))
                    .toList();
            default -> List.of();
        };
    }

    Optional<Finding> check(Subject subject, int line, String keyword, String name, Docker.Argument value) {
        Optional<String> literal = literal(value);
        if (literal.isEmpty() || !Secrets.secret(name, literal.get())) {
            return Optional.empty();
        }
        return Optional.of(new Finding(id(), severity(), subject.blobSha(), line,
                keyword + " " + name + " has a secret-shaped value", Hashes.sha256(literal.get()), List.of()));
    }

    static Optional<String> literal(Docker.Argument value) {
        if (value == null || !value.getContents().stream().allMatch(c -> c instanceof Docker.Literal)) {
            return Optional.empty();
        }
        String text = value.getContents().stream()
                .map(c -> ((Docker.Literal) c).getText())
                .collect(Collectors.joining())
                .strip();
        boolean singleQuoted = text.length() >= 2 && text.startsWith("'") && text.endsWith("'");
        // The tree splits out unquoted variables (rewrite-docker issue 6, fixed in the fork), but a variable inside
        // double quotes stays in the quoted string's text, so a $ outside single quotes still counts as one.
        if (!singleQuoted && text.contains("$")) {
            return Optional.empty();
        }
        if (singleQuoted || text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }
}
