package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class Templates {

    private static final Pattern ANY_MARKER = Pattern.compile("\\{%.*%}|\\{\\{.*}}");
    private static final Pattern JINJA =
            Pattern.compile("\\{%.*%}|\\{\\{\\s+([A-Za-z_]\\w*)(\\.[A-Za-z_]\\w*)*\\s*(\\|[^}]*)?}}");
    private static final Set<String> GO_KEYWORDS = Set.of(
            "end", "else", "if", "range", "with", "define", "template", "block", "break", "continue");

    private Templates() {
    }

    static Optional<String> jinja(String text) {
        return text.lines().filter(line -> !isComment(line) && hasJinjaMarker(line)).map(String::strip).findFirst();
    }

    static Optional<String> anyMarker(String text) {
        return text.lines().filter(line -> ANY_MARKER.matcher(line).find()).map(String::strip).findFirst();
    }

    static boolean hasJinjaMarker(String line) {
        return JINJA.matcher(line).results().anyMatch(marker -> !afterDollar(line, marker.start())
                && (marker.group(1) == null || !GO_KEYWORDS.contains(marker.group(1))));
    }

    static boolean afterDollar(String line, int index) {
        return index > 0 && line.charAt(index - 1) == '$';
    }

    private static boolean isComment(String line) {
        return line.strip().startsWith("#");
    }
}
