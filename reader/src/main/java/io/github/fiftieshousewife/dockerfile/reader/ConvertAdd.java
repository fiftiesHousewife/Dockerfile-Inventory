package io.github.fiftieshousewife.dockerfile.reader;

import io.github.fiftieshousewife.dockerfile.model.Add;
import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class ConvertAdd {

    private static final Pattern HEREDOC = Pattern.compile("<<-?['\"]?\\w+");
    private static final String CHOWN = "--chown=";
    private static final String CHMOD = "--chmod=";
    private static final String CHECKSUM = "--checksum=";
    private static final String KEEP_GIT_DIR = "--keep-git-dir";
    private static final String LINK = "--link";

    private ConvertAdd() {
    }

    static Instruction add(String text) {
        if (HEREDOC.matcher(text.lines().findFirst().orElse("")).find()) {
            return new Raw(text, "ADD with a heredoc");
        }
        List<String> words = ConvertCopy.words(text);
        List<String> flags = words.stream().takeWhile(word -> word.startsWith("--")).toList();
        List<String> written = words.subList(flags.size(), words.size());
        Optional<String> unknown = ConvertCopy.unknownFlag(flags, List.of(CHOWN, CHMOD, CHECKSUM),
                List.of(KEEP_GIT_DIR, LINK));
        if (unknown.isPresent()) {
            return new Raw(text, "ADD with flag " + unknown.get().split("=", 2)[0]);
        }
        Optional<List<String>> paths = ConvertCopy.paths(written);
        if (paths.isEmpty()) {
            return new Raw(text, "ADD in JSON form that is not JSON");
        }
        if (paths.get().size() < 2) {
            return new Raw(text, "ADD without a source and a destination");
        }
        List<String> all = paths.get();
        return new Add(all.subList(0, all.size() - 1), all.getLast(), ConvertCopy.flag(flags, CHOWN),
                ConvertCopy.flag(flags, CHMOD), ConvertCopy.flag(flags, CHECKSUM), flags.contains(KEEP_GIT_DIR),
                flags.contains(LINK), ConvertCopy.jsonForm(written));
    }
}
