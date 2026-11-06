package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/** Variable references in Dockerfile and shell text, which facts reads the same way wherever they appear. */
final class Expansions {

    private static final Pattern REFERENCE =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}|\\$([A-Za-z_][A-Za-z0-9_]*)");

    private Expansions() {
    }

    /** {@code $NAME} and {@code ${NAME}} replaced from {@code variables}; an unknown one stays as written. */
    static String expanded(String text, Map<String, String> variables) {
        return REFERENCE.matcher(text).replaceAll(reference -> Matcher.quoteReplacement(
                variables.getOrDefault(name(reference), reference.group())));
    }

    /** A word holding a command substitution, whose value only the build knows. */
    static boolean holdsCommandSubstitution(String word) {
        return word.contains("$(") || word.contains("`");
    }

    private static String name(MatchResult reference) {
        return reference.group(1) != null ? reference.group(1) : reference.group(2);
    }
}
