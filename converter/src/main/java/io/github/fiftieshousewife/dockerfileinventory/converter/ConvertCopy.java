package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.model.Copy;
import io.github.fiftieshousewife.dockerfile.model.CopyHeredoc;
import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.facts.Continuations;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

final class ConvertCopy {

    private static final Pattern HEREDOC = Pattern.compile("<<-?['\"]?\\w+");
    private static final Pattern PLAIN_HEREDOC = Pattern.compile("COPY\\s+<<(['\"]?)(\\w+)\\1\\s+(\\S+)\\s*");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    private static final String FROM = "--from=";
    private static final String CHOWN = "--chown=";
    private static final String CHMOD = "--chmod=";
    private static final String LINK = "--link";

    private ConvertCopy() {
    }

    static Instruction copy(String text) {
        if (HEREDOC.matcher(text.lines().findFirst().orElse("")).find()) {
            return heredoc(text).<Instruction>map(heredoc -> heredoc)
                    .orElseGet(() -> new Raw(text, "COPY with flags, several heredocs or <<-"));
        }
        List<String> words = words(text);
        List<String> flags = words.stream().takeWhile(word -> word.startsWith("--")).toList();
        List<String> written = words.subList(flags.size(), words.size());
        Optional<String> unknown = unknownFlag(flags, List.of(FROM, CHOWN, CHMOD), List.of(LINK));
        if (unknown.isPresent()) {
            return new Raw(text, "COPY with flag " + unknown.get().split("=", 2)[0]);
        }
        Optional<List<String>> paths = paths(written);
        if (paths.isEmpty()) {
            return new Raw(text, "COPY in JSON form that is not JSON");
        }
        if (paths.get().size() < 2) {
            return new Raw(text, "COPY without a source and a destination");
        }
        List<String> all = paths.get();
        return new Copy(all.subList(0, all.size() - 1), all.getLast(), flag(flags, CHOWN),
                flag(flags, FROM), flag(flags, CHMOD), flags.contains(LINK), jsonForm(written));
    }

    static Optional<CopyHeredoc> heredoc(String text) {
        List<String> lines = text.lines().toList();
        Matcher first = PLAIN_HEREDOC.matcher(lines.getFirst());
        if (!first.matches() || lines.size() < 2 || !lines.getLast().equals(first.group(2))) {
            return Optional.empty();
        }
        return Optional.of(new CopyHeredoc(first.group(3), first.group(2), !first.group(1).isEmpty(),
                lines.subList(1, lines.size() - 1)));
    }

    static List<String> words(String text) {
        List<String> words = List.of(Continuations.oneLine(text).split(" "));
        return words.subList(1, words.size());
    }

    static Optional<String> unknownFlag(List<String> flags, List<String> valued, List<String> bare) {
        return flags.stream()
                .filter(flag -> valued.stream().noneMatch(flag::startsWith) && !bare.contains(flag))
                .findFirst();
    }

    static Optional<List<String>> paths(List<String> written) {
        if (!jsonForm(written)) {
            return Optional.of(written);
        }
        try {
            return Optional.of(JSON.readValue(String.join(" ", written), STRINGS));
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
    }

    static String flag(List<String> flags, String prefix) {
        return flags.stream()
                .filter(flag -> flag.startsWith(prefix))
                .reduce((first, second) -> second)
                .map(flag -> flag.substring(prefix.length()))
                .orElse(null);
    }

    static boolean jsonForm(List<String> written) {
        return !written.isEmpty() && written.getFirst().startsWith("[");
    }
}
