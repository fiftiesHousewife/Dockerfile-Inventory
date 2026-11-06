package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.regex.Pattern;

/**
 * Reduces Buildah's {@code created_by} strings to {@link NormalStep}s. Buildah records instructions as written,
 * variables unexpanded, so after collapsing whitespace a step compares directly with its Dockerfile instruction.
 */
public final class HistoryNormaliser {

    private static final String SHELL = "/bin/sh -c ";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private HistoryNormaliser() {
    }

    public static NormalStep normalise(HistoryStep step) {
        String body = collapse(step.createdBy().substring(SHELL.length()));
        return new NormalStep("RUN", body, null, false);
    }

    private static String collapse(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }
}
