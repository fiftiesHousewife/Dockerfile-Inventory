package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class HistoryNormaliser {

    private static final String SHELL = "/bin/sh -c ";
    private static final String NOP = "#(nop) ";
    private static final String FROM = "FROM ";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // "|2 A=x B=y " before the shell: the build arguments in force after an ARG, not in the Dockerfile.
    private static final Pattern BUILD_ARGUMENTS = Pattern.compile("^\\|\\d+ .*?(?=/bin/sh -c )");
    private static final List<String> KONFLUX_HERMETIC_RUN_PREFIXES = List.of(". /cachi2/cachi2.env && ");
    private static final List<String> KONFLUX_BUILDINFO = List.of("/usr/share/buildinfo/", "/root/buildinfo/");
    private static final List<String> KONFLUX_FINAL_STEP_LABELS = List.of("build-date", "vcs-ref");

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
                yield new NormalStep(keyword, destination, null, konfluxBuildRecords(destination));
            }
            case "LABEL" -> {
                String keys = envKeys(value);
                yield new NormalStep(keyword, keys, null, konfluxFinalStep(keys));
            }
            case "ENV" -> new NormalStep(keyword, envKeys(value), null, false);
            case "USER", "WORKDIR", "EXPOSE", "ENTRYPOINT", "CMD", "VOLUME", "STOPSIGNAL", "SHELL", "ONBUILD", "ARG" ->
                    new NormalStep(keyword, value, null, false);
            default -> null;
        });
    }

    static boolean konfluxBuildRecords(String destination) {
        return KONFLUX_BUILDINFO.stream().anyMatch(destination::startsWith);
    }

    static boolean konfluxFinalStep(String labelKeys) {
        return List.of(labelKeys.split(" ")).containsAll(KONFLUX_FINAL_STEP_LABELS);
    }

    // "dir:<hash> in <dest>": the hash is of the content, so only the destination compares.
    static String destination(String value) {
        return value.substring(value.lastIndexOf(" in ") + " in ".length());
    }

    // ENV and LABEL's legacy form, "k value with spaces", sets one key.
    static String envKeys(String value) {
        String first = value.split(" ", 2)[0];
        return first.contains("=") ? String.join(" ", PairKeys.of(value)) : first.replace("\"", "");
    }

    static String withoutBuilderPrefix(String command) {
        return KONFLUX_HERMETIC_RUN_PREFIXES.stream()
                .filter(command::startsWith)
                .findFirst()
                .map(prefix -> command.substring(prefix.length()))
                .orElse(command);
    }

    private static String collapse(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }
}
