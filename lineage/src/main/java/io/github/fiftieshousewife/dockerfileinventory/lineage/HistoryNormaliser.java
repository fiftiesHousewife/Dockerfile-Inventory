package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reduces Buildah's {@code created_by} strings to {@link NormalStep}s. Buildah records instructions as written,
 * variables unexpanded, so after collapsing whitespace a step compares directly with its Dockerfile instruction.
 */
public final class HistoryNormaliser {

    private static final String SHELL = "/bin/sh -c ";
    private static final String NOP = "#(nop) ";
    private static final String FROM = "FROM ";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /**
     * {@code |2 A=x B=y } before the shell: the build arguments in force when a step after an {@code ARG} ran. The
     * Dockerfile's instruction does not hold them.
     */
    private static final Pattern BUILD_ARGUMENTS = Pattern.compile("^\\|\\d+ .*?(?=/bin/sh -c )");

    /** Text a build service puts before every {@code RUN} (Q13): Konflux's hermetic-build environment. */
    private static final List<String> RUN_PREFIXES = List.of(". /cachi2/cachi2.env && ");

    /** Where Konflux copies its build records, and the labels of the final step it adds to every build (Q13). */
    private static final List<String> BUILDINFO = List.of("/usr/share/buildinfo/", "/root/buildinfo/");
    private static final List<String> BUILD_LABELS = List.of("build-date", "vcs-ref");

    private HistoryNormaliser() {
    }

    public static NormalStep normalise(HistoryStep step) {
        NormalStep instruction = instruction(BUILD_ARGUMENTS.matcher(collapse(step.createdBy() == null ? ""
                : step.createdBy())).replaceFirst(""));
        String comment = step.comment();
        return comment != null && comment.startsWith(FROM) ? instruction.withFrom(comment.substring(FROM.length()))
                : instruction;
    }

    private static NormalStep instruction(String text) {
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
        return metadata(keyword, value).orElseGet(() -> new NormalStep("OTHER", text, null, false));
    }

    private static Optional<NormalStep> metadata(String keyword, String value) {
        return Optional.ofNullable(switch (keyword) {
            case "COPY", "ADD" -> {
                String destination = destination(value);
                yield new NormalStep(keyword, destination, null, BUILDINFO.stream().anyMatch(destination::startsWith));
            }
            case "LABEL" -> {
                String keys = envKeys(value);
                yield new NormalStep(keyword, keys, null, List.of(keys.split(" ")).containsAll(BUILD_LABELS));
            }
            case "ENV" -> new NormalStep(keyword, envKeys(value), null, false);
            case "USER", "WORKDIR", "EXPOSE", "ENTRYPOINT", "CMD", "VOLUME", "STOPSIGNAL", "SHELL", "ONBUILD", "ARG" ->
                    new NormalStep(keyword, value, null, false);
            default -> null;
        });
    }

    /** {@code dir:<hash> in <dest>}: the hash is of the content, so only the destination is comparable. */
    private static String destination(String value) {
        return value.substring(value.lastIndexOf(" in ") + " in ".length());
    }

    /**
     * The keys of {@code key=value} pairs, sorted. Words split on whitespace outside quotes; quotes are removed and
     * a backslash escapes the next character outside single quotes, so a quoted key or a value holding spaces or
     * quotes stays whole. A quote opens only at the start of a key or value: the classic builder writes values
     * unquoted, so {@code MSG=it's} holds an apostrophe, not a quote.
     */
    static List<String> keys(String pairs) {
        List<String> keys = new ArrayList<>();
        StringBuilder key = new StringBuilder();
        boolean inKey = true;
        char quote = 0;
        boolean escaped = false;
        char previous = ' ';
        for (char c : pairs.toCharArray()) {
            boolean opens = quote == 0 && (Character.isWhitespace(previous) || previous == '=');
            previous = c;
            if (quote == '\'') {
                if (c == '\'') {
                    quote = 0;
                } else if (inKey) {
                    key.append(c);
                }
            } else if (escaped) {
                escaped = false;
                if (inKey) {
                    key.append(c);
                }
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"' && (quote == '"' || opens)) {
                quote = quote == '"' ? 0 : '"';
            } else if (c == '\'' && opens) {
                quote = '\'';
            } else if (quote == 0 && Character.isWhitespace(c)) {
                addIfAny(keys, key);
                inKey = true;
            } else if (quote == 0 && c == '=' && inKey) {
                inKey = false;
            } else if (inKey) {
                key.append(c);
            }
        }
        addIfAny(keys, key);
        return keys.stream().sorted().toList();
    }

    /** {@code ENV} or {@code LABEL} {@code k=v ...}, or the legacy {@code k value with spaces}, which sets one. */
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
