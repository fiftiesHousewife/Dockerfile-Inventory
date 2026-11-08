package io.github.fiftieshousewife.dockerfile.reader.internal;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every name an {@code ARG} declares, in order, with its unquoted default or null. */
public final class ArgPairs {

    private ArgPairs() {
    }

    /** An {@code ARG}'s names and their values as written, unquoted, in order; null for a name without one. */
    public static Map<String, String> of(Docker.Arg arg) {
        Map<String, String> pairs = new LinkedHashMap<>();
        declare(pairs, arg.getName(), arg.getValue());
        arg.getAdditionalPairs().forEach(pair -> declare(pairs, pair.getName(), pair.getValue()));
        return pairs;
    }

    static void declare(Map<String, String> pairs, Docker.Literal name, Docker.Argument value) {
        String declared = value == null ? null
                : KeyValuePairs.unquoted(Continuations.removed(ArgumentText.of(value, Map.of())));
        pairs.put(name.getText(), declared == null || ownName(declared, name.getText()) ? null : declared);
    }

    // ARG A=$A names itself; it is read as no default, as when the parser's retry wrote ARG A B that way.
    static boolean ownName(String value, String name) {
        return value.equals("$" + name) || value.equals("${" + name + "}");
    }
}
