package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Templates saved under a Dockerfile's name (Jinja and similar), which are not Dockerfiles. */
final class Templates {

    private static final Pattern ANY_MARKER = Pattern.compile("\\{%.*%}|\\{\\{.*}}");
    /** Jinja only: a block tag, or {@code {{ name.path }}}. Go's {@code {{.Id}}} and {@code {{ index .X }}} differ. */
    private static final Pattern JINJA =
            Pattern.compile("\\{%.*%}|\\{\\{\\s+([A-Za-z_]\\w*)(\\.[A-Za-z_]\\w*)*\\s*(\\|[^}]*)?}}");
    private static final Set<String> GO_KEYWORDS = Set.of(
            "end", "else", "if", "range", "with", "define", "template", "block", "break", "continue");

    private Templates() {
    }

    /**
     * The first line holding Jinja's markers, which make even a file that parses a template: Jinja's
     * {@code {{ spec.version }}} is valid in a {@code FROM} or an {@code ENV}, but no real Dockerfile holds it.
     * Comment lines do not count, nor do a GitHub Actions expression ({@code ${{ github.sha }}}) or a Go template
     * keyword ({@code {{ end }}}), which real Dockerfiles do hold.
     */
    static Optional<String> jinja(String text) {
        return text.lines().filter(line -> !isComment(line) && hasJinjaMarker(line)).map(String::strip).findFirst();
    }

    /**
     * The first line holding Jinja or Go template markers ({@code {% if %}}, {@code {{ spec.version }}}). Asked only
     * of a file that failed to parse, so a {@code docker inspect --format '{{.Id}}'} in a valid file is not one.
     */
    static Optional<String> anyMarker(String text) {
        return text.lines().filter(line -> ANY_MARKER.matcher(line).find()).map(String::strip).findFirst();
    }

    private static boolean hasJinjaMarker(String line) {
        return JINJA.matcher(line).results().anyMatch(marker -> !afterDollar(line, marker.start())
                && (marker.group(1) == null || !GO_KEYWORDS.contains(marker.group(1))));
    }

    private static boolean afterDollar(String line, int index) {
        return index > 0 && line.charAt(index - 1) == '$';
    }

    private static boolean isComment(String line) {
        return line.strip().startsWith("#");
    }
}
