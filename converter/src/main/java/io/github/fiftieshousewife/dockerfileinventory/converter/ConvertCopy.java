package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.facts.Continuations;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class ConvertCopy {

    private static final Pattern HEREDOC = Pattern.compile("<<-?['\"]?\\w+");
    private static final String FROM = "--from=";
    private static final String CHOWN = "--chown=";

    private ConvertCopy() {
    }

    static Instruction copy(String text) {
        if (HEREDOC.matcher(text.lines().findFirst().orElse("")).find()) {
            return new Instruction.Raw(text, "COPY with a heredoc");
        }
        List<String> words = words(text);
        List<String> flags = words.stream().takeWhile(word -> word.startsWith("--")).toList();
        List<String> paths = words.subList(flags.size(), words.size());
        Optional<String> unknown = unknownFlag(flags);
        if (unknown.isPresent()) {
            return new Instruction.Raw(text, "COPY with flag " + unknown.get().split("=", 2)[0]);
        }
        if (paths.isEmpty() || paths.getFirst().startsWith("[")) {
            return new Instruction.Raw(text, "COPY in JSON form");
        }
        if (paths.size() < 2) {
            return new Instruction.Raw(text, "COPY without a source and a destination");
        }
        return new Instruction.Copy(paths.subList(0, paths.size() - 1), paths.getLast(), flag(flags, CHOWN),
                flag(flags, FROM));
    }

    static List<String> words(String text) {
        List<String> words = List.of(Continuations.oneLine(text).split(" "));
        return words.subList(1, words.size());
    }

    static Optional<String> unknownFlag(List<String> flags) {
        return flags.stream().filter(flag -> !flag.startsWith(FROM) && !flag.startsWith(CHOWN)).findFirst();
    }

    static String flag(List<String> flags, String prefix) {
        return flags.stream()
                .filter(flag -> flag.startsWith(prefix))
                .reduce((first, second) -> second)
                .map(flag -> flag.substring(prefix.length()))
                .orElse(null);
    }
}
