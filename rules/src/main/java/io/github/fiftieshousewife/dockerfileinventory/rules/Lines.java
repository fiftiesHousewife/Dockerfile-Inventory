package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.openrewrite.Cursor;
import org.openrewrite.docker.tree.Docker;

final class Lines {

    private final Map<Docker, Integer> lines;

    private Lines(Map<Docker, Integer> lines) {
        this.lines = lines;
    }

    static Lines of(Docker.File file) {
        Map<Docker, Integer> lines = new IdentityHashMap<>();
        Cursor cursor = new Cursor(null, file);
        String text = file.printAll();
        int from = 0;
        for (Docker node : ordered(file)) {
            String own = withoutLeadingComments(node.printTrimmed(cursor));
            int at = startOfLine(text, own, from);
            if (at >= 0) {
                lines.put(node, 1 + newlines(text.substring(0, at)));
                from = at + own.length();
            }
        }
        return new Lines(lines);
    }

    static List<Docker> ordered(Docker.File file) {
        List<Docker> ordered = new ArrayList<>(file.getGlobalArgs());
        file.getStages().forEach(stage -> {
            ordered.add(stage.getFrom());
            ordered.addAll(stage.getInstructions());
        });
        return ordered;
    }

    static String withoutLeadingComments(String printed) {
        String rest = printed.stripLeading();
        while (rest.startsWith("#")) {
            int newline = rest.indexOf('\n');
            rest = newline < 0 ? "" : rest.substring(newline + 1).stripLeading();
        }
        return rest;
    }

    static int startOfLine(String text, String own, int from) {
        for (int at = text.indexOf(own, from); at >= 0; at = text.indexOf(own, at + 1)) {
            int lineStart = text.lastIndexOf('\n', at - 1) + 1;
            if (text.substring(lineStart, at).isBlank()) {
                return at;
            }
        }
        return -1;
    }

    int of(Docker instruction) {
        return lines.getOrDefault(instruction, 0);
    }

    static int newlines(CharSequence text) {
        return (int) text.chars().filter(c -> c == '\n').count();
    }
}
