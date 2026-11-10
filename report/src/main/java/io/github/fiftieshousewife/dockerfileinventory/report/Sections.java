package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Arrays;
import java.util.stream.Collectors;

// Every page's sections fold under their headings, open to begin with; a page is read top to bottom, then skimmed.
final class Sections {

    private static final String HEADING = "<h2>";

    private Sections() {
    }

    static String foldable(String body) {
        int first = body.indexOf(HEADING);
        if (first < 0) {
            return body;
        }
        return body.substring(0, first) + Arrays.stream(body.substring(first + HEADING.length()).split(HEADING, -1))
                .map(Sections::section).collect(Collectors.joining());
    }

    static String section(String afterHeading) {
        int end = afterHeading.indexOf("</h2>") + "</h2>".length();
        return "<details class=\"section\" open><summary>" + HEADING + afterHeading.substring(0, end) + "</summary>"
                + afterHeading.substring(end) + "</details>";
    }
}
