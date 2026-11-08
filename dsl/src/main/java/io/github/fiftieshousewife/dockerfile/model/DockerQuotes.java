package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class DockerQuotes {

    private DockerQuotes() {
    }

    // Double quotes keep spaces and let $VARIABLES expand as Docker expands them. The model's escaped pairs
    // (\$ a literal dollar, \\ a literal backslash) are kept; any other backslash is doubled and a quote escaped.
    static String doubleQuoted(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            char next = i + 1 < value.length() ? value.charAt(i + 1) : 0;
            if (c == '\\' && (next == '\\' || next == '$')) {
                quoted.append(c).append(next);
                i++;
            } else if (c == '\\' || c == '"') {
                quoted.append('\\').append(c);
            } else {
                quoted.append(c);
            }
        }
        return quoted.append('"').toString();
    }

    static String jsonArray(List<String> command) {
        return command.stream().map(DockerQuotes::jsonString).collect(Collectors.joining(", ", "[", "]"));
    }

    // JSON has no \$, so every backslash is escaped; control characters too, keeping the instruction on one line.
    static String jsonString(String value) {
        StringBuilder json = new StringBuilder("\"");
        value.chars().forEach(c -> json.append(switch (c) {
            case '\\' -> "\\\\";
            case '"' -> "\\\"";
            case '\n' -> "\\n";
            case '\t' -> "\\t";
            case '\r' -> "\\r";
            default -> c < 0x20 ? String.format(Locale.ROOT, "\\u%04x", c) : String.valueOf((char) c);
        }));
        return json.append('"').toString();
    }
}
