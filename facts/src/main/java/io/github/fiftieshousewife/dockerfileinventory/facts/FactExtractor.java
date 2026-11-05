package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.HashMap;
import java.util.Map;
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
        Docker.From from = file.getStages().getLast().getFrom();
        return new ImageFacts(file.getStages().size(), base(from, globalArgs));
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
