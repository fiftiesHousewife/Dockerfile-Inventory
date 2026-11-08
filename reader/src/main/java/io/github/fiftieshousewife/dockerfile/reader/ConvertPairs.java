package io.github.fiftieshousewife.dockerfile.reader;

import io.github.fiftieshousewife.dockerfile.reader.internal.Values;
import io.github.fiftieshousewife.dockerfile.reader.internal.ArgPairs;
import io.github.fiftieshousewife.dockerfile.model.Arg;
import io.github.fiftieshousewife.dockerfile.model.Env;
import io.github.fiftieshousewife.dockerfile.model.Label;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class ConvertPairs {

    private ConvertPairs() {
    }

    // Facts' reader says which names have a default; the values are put in the model's form here.
    static List<Arg> args(Docker.Arg arg) {
        Map<String, String> declared = ArgPairs.of(arg);
        Map<String, String> values = new HashMap<>();
        values.put(arg.getName().getText(), modelForm(arg.getValue()));
        arg.getAdditionalPairs().forEach(pair -> values.put(pair.getName().getText(), modelForm(pair.getValue())));
        return declared.entrySet().stream()
                .map(e -> new Arg(e.getKey(), e.getValue() == null ? null : values.get(e.getKey())))
                .toList();
    }

    static String modelForm(Docker.Argument value) {
        return value == null ? "" : Pairs.modelForm(Continuations.removed(Values.text(value)));
    }

    static Env env(String printed) {
        return new Env(pairs(afterKeyword(printed)));
    }

    static Label label(String printed) {
        return new Label(pairs(afterKeyword(printed)));
    }

    static String afterKeyword(String instruction) {
        String text = instruction.lines()
                .filter(line -> !line.strip().startsWith("#"))
                .collect(Collectors.joining("\n"));
        String[] parts = Continuations.removed(text).strip().split("\\s+", 2);
        return parts.length < 2 ? "" : parts[1];
    }

    // Read from the text, as each value is needed as written, quotes and escapes kept, for the model's form.
    static Map<String, String> pairs(String arguments) {
        String first = arguments.split("\\s+", 2)[0];
        if (first.contains("=")) {
            return Pairs.parse(arguments);
        }
        String value = arguments.substring(first.length()).strip();
        return Map.of(Values.unquote(first), Values.unquote(value));
    }
}
