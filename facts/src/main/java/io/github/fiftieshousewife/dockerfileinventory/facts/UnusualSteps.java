package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class UnusualSteps {

    private static final Set<String> UNUSUAL_INSTRUCTIONS =
            Set.of("ONBUILD", "SHELL", "HEALTHCHECK", "VOLUME", "STOPSIGNAL", "MAINTAINER");
    private static final List<String> RUN_FLAGS = List.of("--mount", "--network", "--security");
    private static final List<String> COPY_FLAGS = List.of("--link", "--parents", "--exclude");

    private UnusualSteps() {
    }

    // A line as printed on one line; empty for an everyday instruction in its everyday form.
    static Optional<String> of(String line) {
        List<String> words = KeyValuePairs.words(line);
        String keyword = words.getFirst().toUpperCase(Locale.ROOT);
        List<String> rest = words.subList(1, words.size());
        List<String> flags = rest.stream().takeWhile(w -> w.startsWith("--")).toList();
        String first = rest.size() > flags.size() ? rest.get(flags.size()) : "";
        return switch (keyword) {
            case "ADD" -> Optional.of(rest.stream().anyMatch(StageCopies::remote) ? "ADD of a URL" : "ADD");
            case "RUN" -> flagged("RUN", flags, RUN_FLAGS).or(() -> first.startsWith("[")
                    ? Optional.of("RUN in exec form")
                    : first.startsWith("<<") ? Optional.of("RUN with a heredoc") : Optional.empty());
            case "COPY" -> first.startsWith("<<") ? Optional.of("COPY with a heredoc")
                    : flagged("COPY", flags, COPY_FLAGS);
            default -> UNUSUAL_INSTRUCTIONS.contains(keyword) ? Optional.of(keyword) : Optional.empty();
        };
    }

    private static Optional<String> flagged(String keyword, List<String> flags, List<String> unusual) {
        return flags.stream().map(f -> f.split("=", 2)[0]).filter(unusual::contains).findFirst()
                .map(flag -> keyword + " " + flag);
    }
}
