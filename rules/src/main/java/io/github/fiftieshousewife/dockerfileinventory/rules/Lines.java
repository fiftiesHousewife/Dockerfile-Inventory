package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.openrewrite.Cursor;
import org.openrewrite.docker.tree.Docker;

/**
 * Line numbers for instructions, which the tree does not store. Each instruction's own text is found in the printed
 * file, in file order, where it starts a line; its line is one more than the newlines before it.
 */
final class Lines {

    private final Map<Docker, Integer> lines;

    private Lines(Map<Docker, Integer> lines) {
        this.lines = lines;
    }

    static Lines of(Docker.File file) {
        List<Docker> ordered = new ArrayList<>(file.getGlobalArgs());
        file.getStages().forEach(stage -> {
            ordered.add(stage.getFrom());
            ordered.addAll(stage.getInstructions());
        });
        Map<Docker, Integer> lines = new IdentityHashMap<>();
        Cursor cursor = new Cursor(null, file);
        String text = file.printAll();
        int from = 0;
        for (Docker node : ordered) {
            String own = withoutLeadingComments(node.printTrimmed(cursor));
            int at = startOfLine(text, own, from);
            if (at >= 0) {
                lines.put(node, 1 + newlines(text.substring(0, at)));
                from = at + own.length();
            }
        }
        return new Lines(lines);
    }

    /** The printed prefix can carry the comment lines before an instruction; its own text starts after them. */
    private static String withoutLeadingComments(String printed) {
        String rest = printed.stripLeading();
        while (rest.startsWith("#")) {
            int newline = rest.indexOf('\n');
            rest = newline < 0 ? "" : rest.substring(newline + 1).stripLeading();
        }
        return rest;
    }

    /** The next place {@code own} starts a line (only whitespace before it on that line), so a comment never matches. */
    private static int startOfLine(String text, String own, int from) {
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

    private static int newlines(CharSequence text) {
        return (int) text.chars().filter(c -> c == '\n').count();
    }
}
