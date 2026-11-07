package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.facts.KeyValuePairs;
import java.util.LinkedHashMap;
import java.util.Map;

final class Pairs {

    private Pairs() {
    }

    static Map<String, String> parse(String text) {
        Map<String, String> pairs = new LinkedHashMap<>();
        KeyValuePairs.of(text).forEach(p -> pairs.put(p.key(), p.value() == null ? "" : modelForm(p.value())));
        return pairs;
    }

    // Docker's quoting. The model's form is the text as written inside double quotes: $NAME expands, \$ is a literal
    // dollar and \\ a literal backslash. Inside "..." a backslash escapes only a quote, a backslash or a dollar (and
    // joins a continued line); before anything else it is a literal backslash. Inside '...' nothing is special.
    // Unquoted, a backslash escapes the next character.
    static String modelForm(String written) {
        StringBuilder value = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < written.length(); i++) {
            char c = written.charAt(i);
            char next = i + 1 < written.length() ? written.charAt(i + 1) : 0;
            if (quote == '\'') {
                quote = c == '\'' ? 0 : quote;
                value.append(c == '\'' ? "" : literal(c));
            } else if (c == '\\' && quote == '"') {
                i += escapedInDoubleQuotes(next, value);
            } else if (c == '\\') {
                value.append(literal(next));
                i++;
            } else if (c == '"') {
                quote = quote == '"' ? 0 : '"';
            } else if (c == '\'' && quote == 0) {
                quote = '\'';
            } else {
                value.append(c);
            }
        }
        return value.toString();
    }

    static String literal(char c) {
        return c == '$' || c == '\\' ? "\\" + c : c == 0 ? "" : String.valueOf(c);
    }

    static int escapedInDoubleQuotes(char next, StringBuilder value) {
        switch (next) {
            case '"' -> value.append('"');
            case '\\', '$' -> value.append('\\').append(next);
            case '\n' -> {
            }
            default -> {
                value.append("\\\\");
                return 0;
            }
        }
        return 1;
    }
}
