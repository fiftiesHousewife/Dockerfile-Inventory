package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.ArrayList;
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
        String text = collapse(step.createdBy() == null ? "" : step.createdBy());
        if (!text.startsWith(SHELL)) {
            return new NormalStep("OTHER", text, null, false);
        }
        String body = text.substring(SHELL.length());
        if (!body.startsWith(NOP)) {
            return new NormalStep("RUN", withoutBuilderPrefix(body), null, false);
        }
        String instruction = body.substring(NOP.length());
        int space = instruction.indexOf(' ');
        String keyword = space < 0 ? instruction : instruction.substring(0, space);
        String value = space < 0 ? "" : instruction.substring(space + 1);
        return switch (keyword) {
            case "COPY", "ADD" -> new NormalStep(keyword, destination(value), null, false);
            case "LABEL" -> new NormalStep(keyword, String.join(" ", keys(value)), null, false);
            case "ENV" -> new NormalStep(keyword, envKeys(value), null, false);
            case "USER", "WORKDIR", "EXPOSE", "ENTRYPOINT", "CMD", "VOLUME", "STOPSIGNAL", "SHELL", "ONBUILD" ->
                    new NormalStep(keyword, value, null, false);
            default -> new NormalStep("OTHER", text, null, false);
        };
    }

    /** {@code dir:<hash> in <dest>}: the hash is of the content, so only the destination is comparable. */
    private static String destination(String value) {
        return value.substring(value.lastIndexOf(" in ") + " in ".length());
    }

    /**
     * The keys of {@code key=value} pairs, sorted. Words split on whitespace outside double quotes; quotes are removed
     * and a backslash escapes the next character, so a quoted key or a value holding spaces or quotes stays whole.
     */
    static List<String> keys(String pairs) {
        List<String> keys = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        boolean inKey = true;
        boolean quoted = false;
        boolean escaped = false;
        for (char c : pairs.toCharArray()) {
            if (escaped) {
                escaped = false;
                if (inKey) {
                    key.append(c);
                }
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && Character.isWhitespace(c)) {
                addIfAny(keys, key);
                inKey = true;
            } else if (!quoted && c == '=' && inKey) {
                inKey = false;
            } else if (inKey) {
                key.append(c);
            }
        }
        addIfAny(keys, key);
        return keys.stream().sorted().toList();
    }

    /** {@code ENV k=v ...}, or the legacy {@code ENV k value with spaces}, which sets one variable. */
    private static String envKeys(String value) {
        String first = value.split(" ", 2)[0];
        return first.contains("=") ? String.join(" ", keys(value)) : first.replace("\"", "");
    }

    private static void addIfAny(List<String> keys, StringBuilder key) {
        if (!key.isEmpty()) {
            keys.add(key.toString());
            key.setLength(0);
        }
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
