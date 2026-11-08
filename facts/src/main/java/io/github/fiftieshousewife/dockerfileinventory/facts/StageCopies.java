package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

final class StageCopies {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private StageCopies() {
    }

    // A line as printed on one line: "COPY [--flag=value...] source... destination", or its JSON or heredoc form.
    static CopyFact copy(String line, Set<String> earlierStages) {
        List<String> words = KeyValuePairs.words(line);
        String instruction = words.getFirst().toUpperCase(Locale.ROOT);
        List<String> flags = words.subList(1, words.size()).stream().takeWhile(w -> w.startsWith("--")).toList();
        List<String> written = words.subList(1 + flags.size(), words.size());
        String from = flag(flags, "--from=");
        String chown = flag(flags, "--chown=");
        String chmod = flag(flags, "--chmod=");
        if (!written.isEmpty() && written.getFirst().startsWith("<<")) {
            return new CopyFact(instruction, List.of(), written.getLast(), "heredoc", null, chown, chmod);
        }
        List<String> paths = paths(written);
        List<String> sources = paths.isEmpty() ? List.of() : paths.subList(0, paths.size() - 1);
        String destination = paths.isEmpty() ? null : paths.getLast();
        return new CopyFact(instruction, sources, destination, origin(from, sources, earlierStages), from, chown,
                chmod);
    }

    static String origin(String from, List<String> sources, Set<String> earlierStages) {
        if (from != null) {
            return earlierStages.contains(from) || from.matches("\\d+") ? "stage" : "image";
        }
        return sources.stream().anyMatch(StageCopies::remote) ? "url" : "context";
    }

    static boolean remote(String source) {
        return source.startsWith("http://") || source.startsWith("https://") || source.startsWith("git@");
    }

    private static List<String> paths(List<String> written) {
        String joined = String.join(" ", written);
        if (joined.startsWith("[")) {
            try {
                return JSON.readValue(joined, STRINGS);
            } catch (JacksonException notJson) {
                return written.stream().map(KeyValuePairs::unquoted).toList();
            }
        }
        return written.stream().map(KeyValuePairs::unquoted).toList();
    }

    private static String flag(List<String> flags, String prefix) {
        return flags.stream().filter(f -> f.startsWith(prefix)).reduce((first, second) -> second)
                .map(f -> f.substring(prefix.length())).orElse(null);
    }
}
