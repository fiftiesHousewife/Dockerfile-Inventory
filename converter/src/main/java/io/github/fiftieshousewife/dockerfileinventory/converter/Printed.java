package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.List;
import org.openrewrite.Cursor;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class Printed {

    private final Cursor cursor;

    Printed(Docker.File file) {
        this.cursor = new Cursor(null, file);
    }

    List<String> comments(Docker.Instruction instruction) {
        return instruction.printTrimmed(cursor).lines()
                .map(String::strip)
                .takeWhile(line -> line.startsWith("#"))
                .map(line -> line.substring(1).strip())
                .toList();
    }

    String text(Docker.Instruction instruction) {
        String text = instruction.printTrimmed(cursor).stripLeading();
        while (text.startsWith("#")) {
            int newline = text.indexOf('\n');
            text = newline < 0 ? "" : text.substring(newline + 1).stripLeading();
        }
        return text;
    }
}
