package io.github.fiftieshousewife.dockerfile.reader.internal;

import io.github.fiftieshousewife.dockerfile.reader.internal.ShellToken.Word;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ShellCommand {

    private static final Pattern ASSIGNMENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*=.*", Pattern.DOTALL);
    private static final Pattern REDIRECT = Pattern.compile("\\d*(&>>?|>>?|<)(.*)");

    private final List<String> words = new ArrayList<>();
    private boolean redirectTargetNext;

    void add(Word word, Map<String, String> assignments) {
        String text = word.text();
        Matcher redirect = REDIRECT.matcher(text);
        if (redirectTargetNext) {
            redirectTargetNext = false;
        } else if (!word.quoted() && redirect.matches()) {
            redirectTargetNext = redirect.group(2).isEmpty();
        } else if (ShellKeywords.BRANCHES.containsAll(words) && ASSIGNMENT.matcher(text).matches()) {
            assign(word.marked(), assignments);
        } else {
            words.add(text);
        }
    }

    static void assign(String marked, Map<String, String> assignments) {
        String value = marked.substring(marked.indexOf('=') + 1);
        assignments.put(marked.substring(0, marked.indexOf('=')),
                Expansions.expanded(value, assignments).replace(ShellToken.LITERAL_DOLLAR, '$'));
    }

    void dropKeywords() {
        ShellKeywords.dropLeading(words);
        ShellKeywords.dropFunctionHeader(words);
    }

    Optional<Map.Entry<String, String>> forAssignment(Map<String, String> assignments) {
        return ShellKeywords.forAssignment(words, assignments);
    }

    boolean firstWordIs(String keyword) {
        return !words.isEmpty() && words.getFirst().equals(keyword);
    }

    boolean isEmpty() {
        return words.isEmpty();
    }

    String first() {
        return words.getFirst();
    }

    void removeFirst() {
        words.removeFirst();
    }

    void clear() {
        words.clear();
    }

    List<String> take() {
        List<String> taken = List.copyOf(words);
        words.clear();
        return taken;
    }
}
