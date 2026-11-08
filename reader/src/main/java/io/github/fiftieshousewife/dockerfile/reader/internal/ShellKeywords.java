package io.github.fiftieshousewife.dockerfile.reader.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ShellKeywords {

    static final Set<String> BRANCHES =
            Set.of("if", "then", "elif", "else", "fi", "{", "}", "while", "until", "do", "done");
    private static final Set<String> UNREAD = Set.of("select");
    private static final Set<String> SHELLS = Set.of("sh", "bash", "ash", "dash", "zsh", "ksh");

    private ShellKeywords() {
    }

    static void dropLeading(List<String> command) {
        while (!command.isEmpty() && BRANCHES.contains(command.getFirst())) {
            command.removeFirst();
        }
    }

    static void dropFunctionHeader(List<String> command) {
        if (command.size() >= 2 && command.getFirst().equals("function")) {
            command.subList(0, command.size() > 2 && command.get(2).equals("{") ? 3 : 2).clear();
        }
    }

    static Optional<Map.Entry<String, String>> forAssignment(List<String> command, Map<String, String> assignments) {
        if (command.size() < 3 || !command.get(2).equals("in")) {
            return Optional.empty();
        }
        List<String> words = command.subList(3, command.size());
        if (words.stream().anyMatch(ShellKeywords::onlyTheBuildKnows)) {
            return Optional.empty();
        }
        return Optional.of(Map.entry(command.get(1), Expansions.expanded(String.join(" ", words), assignments)));
    }

    static boolean onlyTheBuildKnows(String word) {
        return word.contains("*") || word.contains("?") || Expansions.holdsCommandSubstitution(word);
    }

    static boolean unreadable(String first, boolean pipedInto) {
        return UNREAD.contains(first) || pipedInto && SHELLS.contains(first);
    }
}
