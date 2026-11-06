package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Reduces Buildah's {@code created_by} strings to {@link NormalStep}s. Buildah records instructions as written,
 * variables unexpanded, so after collapsing whitespace a step compares directly with its Dockerfile instruction.
 */
public final class HistoryNormaliser {

    private static final String SHELL = "/bin/sh -c ";
    private static final String NOP = "#(nop) ";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Text a build service puts before every {@code RUN} (Q13): Konflux's hermetic-build environment. */
    private static final List<String> RUN_PREFIXES = List.of(". /cachi2/cachi2.env && ");

    private HistoryNormaliser() {
    }

    public static NormalStep normalise(HistoryStep step) {
        String body = collapse(step.createdBy().substring(SHELL.length()));
        if (!body.startsWith(NOP)) {
            return new NormalStep("RUN", withoutBuilderPrefix(body), null, false);
        }
        String instruction = body.substring(NOP.length());
        int space = instruction.indexOf(' ');
        String keyword = space < 0 ? instruction : instruction.substring(0, space);
        String value = space < 0 ? "" : instruction.substring(space + 1);
        return switch (keyword) {
            case "COPY", "ADD" -> new NormalStep(keyword, destination(value), null, false);
            default -> new NormalStep("OTHER", body, null, false);
        };
    }

    /** {@code dir:<hash> in <dest>}: the hash is of the content, so only the destination is comparable. */
    private static String destination(String value) {
        return value.substring(value.lastIndexOf(" in ") + " in ".length());
    }

    private static String withoutBuilderPrefix(String command) {
        return RUN_PREFIXES.stream()
                .filter(command::startsWith)
                .findFirst()
                .map(prefix -> command.substring(prefix.length()))
                .orElse(command);
    }

    private static String collapse(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }
}
