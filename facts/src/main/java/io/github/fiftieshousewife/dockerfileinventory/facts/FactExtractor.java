package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.openrewrite.docker.tree.Docker;

/** Reduces a parsed Dockerfile to {@link ImageFacts}. With {@link FactsParser}, the only code that sees rewrite types. */
public final class FactExtractor {

    private FactExtractor() {
    }

    public static ImageFacts extract(Docker.File file) {
        Map<String, String> globalArgs = new HashMap<>();
        file.getGlobalArgs().forEach(arg -> {
            if (arg.getValue() != null) {
                globalArgs.put(arg.getName().getText(), text(arg.getValue(), globalArgs));
            }
        });
        List<StagedCall> calls = new ArrayList<>();
        int runs = 0;
        int opaque = 0;
        List<Docker.Stage> stages = file.getStages();
        for (int stage = 0; stage < stages.size(); stage++) {
            for (Docker.Instruction instruction : stages.get(stage).getInstructions()) {
                if (instruction instanceof Docker.Run run) {
                    runs++;
                    Optional<List<List<String>>> commands = commands(run);
                    if (commands.isEmpty()) {
                        opaque++;
                    }
                    int index = stage;
                    commands.ifPresent(c -> PackageCalls.of(c).forEach(call -> calls.add(new StagedCall(index, call))));
                }
            }
        }
        return new ImageFacts(stages.size(), finalBase(file, globalArgs), calls, runs, opaque);
    }

    /** The final stage's base; a stage built {@code FROM} an earlier stage's name takes that stage's base. */
    static BaseImage finalBase(Docker.File file, Map<String, String> args) {
        Map<String, BaseImage> byStageName = new HashMap<>();
        BaseImage base = null;
        for (Docker.Stage stage : file.getStages()) {
            BaseImage from = base(stage.getFrom(), args);
            base = from.tag() == null && from.digest() == null ? byStageName.getOrDefault(from.name(), from) : from;
            if (stage.getFrom().getAs() != null) {
                byStageName.put(stage.getFrom().getAs().getName().getText(), base);
            }
        }
        return base;
    }

    /**
     * The parser splits name, tag and digest before variables are known, so a {@code FROM ${BASE}} whose default
     * carries a tag would keep the tag in the name. The reference is rebuilt, substituted, then split here.
     */
    static BaseImage base(Docker.From from, Map<String, String> args) {
        String reference = text(from.getImageName(), args)
                + (from.getTag() == null ? "" : ":" + text(from.getTag(), args))
                + (from.getDigest() == null ? "" : "@" + text(from.getDigest(), args));
        return split(reference);
    }

    static BaseImage split(String reference) {
        int at = reference.indexOf('@');
        String digest = at < 0 ? null : reference.substring(at + 1);
        String nameAndTag = at < 0 ? reference : reference.substring(0, at);
        int colon = nameAndTag.lastIndexOf(':');
        boolean hasTag = colon > nameAndTag.lastIndexOf('/');
        return new BaseImage(hasTag ? nameAndTag.substring(0, colon) : nameAndTag,
                hasTag ? nameAndTag.substring(colon + 1) : null, digest);
    }

    /**
     * A {@code RUN}'s simple commands: the tokenised shell form or heredoc script, or the exec form's words as one
     * command. Empty when the tokeniser declines the script.
     */
    static Optional<List<List<String>>> commands(Docker.Run run) {
        return switch (run.getCommand()) {
            case Docker.ShellForm shell -> ShellTokeniser.parse(shell.getArgument().getText());
            case Docker.ExecForm exec -> Optional.of(List.of(exec.getArguments().stream().map(Docker.Literal::getText).toList()));
            case Docker.HeredocForm heredoc -> ShellTokeniser.parse(heredoc.getBodies().stream()
                    .flatMap(b -> b.getContentLines().stream())
                    .collect(Collectors.joining("\n")));
            default -> Optional.empty();
        };
    }

    /** An argument's text with known variables substituted; an unknown one is kept as {@code ${NAME}}. */
    private static String text(Docker.Argument argument, Map<String, String> args) {
        return argument.getContents().stream()
                .map(c -> c instanceof Docker.Literal l ? l.getText() : variable((Docker.EnvironmentVariable) c, args))
                .collect(Collectors.joining());
    }

    private static String variable(Docker.EnvironmentVariable variable, Map<String, String> args) {
        return args.getOrDefault(variable.getName(), "${" + variable.getName() + "}");
    }
}
