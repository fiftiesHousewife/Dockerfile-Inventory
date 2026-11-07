package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Instruction;
import io.github.fiftieshousewife.dockerfileinventory.facts.Continuations;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactExtractor;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class ConvertPairs {

    private ConvertPairs() {
    }

    // rewrite-docker folds ARG A=1 B=2 into one name (upstream issue 12): the names come from facts' reader.
    static List<Instruction.Arg> args(Docker.Arg arg) {
        Map<String, String> declared = FactExtractor.argPairs(arg);
        Docker.Argument value = arg.getValue();
        Map<String, String> values = Pairs.parse(Continuations.removed(
                arg.getName().getText() + (value == null ? "" : "=" + Values.text(value))));
        return declared.entrySet().stream()
                .map(e -> new Instruction.Arg(e.getKey(), e.getValue() == null ? null : values.get(e.getKey())))
                .toList();
    }

    static Instruction.Env env(String printed) {
        return new Instruction.Env(pairs(afterKeyword(printed)));
    }

    static Instruction.Label label(String printed) {
        return new Instruction.Label(pairs(afterKeyword(printed)));
    }

    static String afterKeyword(String instruction) {
        String text = instruction.lines()
                .filter(line -> !line.strip().startsWith("#"))
                .collect(Collectors.joining("\n"));
        String[] parts = Continuations.removed(text).strip().split("\\s+", 2);
        return parts.length < 2 ? "" : parts[1];
    }

    // Read from the text: rewrite-docker 8.90.4 mis-reads pairs in several layouts (upstream issues 4-6).
    static Map<String, String> pairs(String arguments) {
        String first = arguments.split("\\s+", 2)[0];
        if (first.contains("=")) {
            return Pairs.parse(arguments);
        }
        String value = arguments.substring(first.length()).strip();
        return Map.of(Values.unquote(first), Values.unquote(value));
    }
}
