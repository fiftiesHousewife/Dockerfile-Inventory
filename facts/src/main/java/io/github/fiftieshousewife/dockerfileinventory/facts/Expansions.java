package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

final class Expansions {

    private static final Pattern REFERENCE =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?:\\[[@*]])?}|\\$([A-Za-z_][A-Za-z0-9_]*)");

    private Expansions() {
    }

    static String expanded(String text, Map<String, String> variables) {
        return REFERENCE.matcher(text).replaceAll(reference -> Matcher.quoteReplacement(
                variables.getOrDefault(name(reference), reference.group())));
    }

    static boolean holdsCommandSubstitution(String word) {
        return word.contains("$(") || word.contains("`");
    }

    private static String name(MatchResult reference) {
        return Objects.requireNonNullElse(reference.group(1), reference.group(2));
    }
}
