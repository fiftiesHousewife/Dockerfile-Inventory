package io.github.fiftieshousewife.dockerfile.reader;

import java.util.List;
import java.util.stream.Stream;
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
        return Stream.iterate(instruction.printTrimmed(cursor).stripLeading(), Printed::afterFirstLine)
                .filter(text -> !text.startsWith("#"))
                .findFirst()
                .orElseThrow();
    }

    static String afterFirstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? "" : text.substring(newline + 1).stripLeading();
    }
}
