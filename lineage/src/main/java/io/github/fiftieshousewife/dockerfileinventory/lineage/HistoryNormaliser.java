package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class HistoryNormaliser {

    private static final String SHELL = "/bin/sh -c ";
    private static final String NOP = "#(nop) ";
    private static final String FROM = "FROM ";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // "|2 A=x B=y " before the shell: the build arguments in force when a step after an ARG ran, which the
    // Dockerfile's instruction does not hold.
    private static final Pattern BUILD_ARGUMENTS = Pattern.compile("^\\|\\d+ .*?(?=/bin/sh -c )");
    // Konflux's hermetic-build environment, put before every RUN.
    private static final List<String> RUN_PREFIXES = List.of(". /cachi2/cachi2.env && ");
    // Where Konflux copies its build records, and the labels of the final step it adds to every build.
    private static final List<String> BUILDINFO = List.of("/usr/share/buildinfo/", "/root/buildinfo/");
    private static final List<String> BUILD_LABELS = List.of("build-date", "vcs-ref");

    private HistoryNormaliser() {
    }

    static NormalStep normalise(HistoryStep step) {
        String createdBy = step.createdBy() == null ? "" : step.createdBy();
        NormalStep instruction = instruction(BUILD_ARGUMENTS.matcher(collapse(createdBy)).replaceFirst(""));
        String comment = step.comment();
        return comment != null && comment.startsWith(FROM) ? instruction.withFrom(comment.substring(FROM.length()))
                : instruction;
    }

    static NormalStep instruction(String text) {
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

    static Optional<NormalStep> metadata(String keyword, String value) {
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

    // "dir:<hash> in <dest>": the hash is of the content, so only the destination is comparable.
    static String destination(String value) {
        return value.substring(value.lastIndexOf(" in ") + " in ".length());
    }

    // The legacy form "k value with spaces" sets one key.
    static String envKeys(String value) {
        String first = value.split(" ", 2)[0];
        return first.contains("=") ? String.join(" ", PairKeys.of(value)) : first.replace("\"", "");
    }

    static String withoutBuilderPrefix(String command) {
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
