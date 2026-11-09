package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import io.github.fiftieshousewife.dockerfileinventory.facts.RunScripts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import org.openrewrite.Cursor;

final class RemovedLater implements Rule {

    record Copied(String path, String source, String instruction, int line) {
    }

    @Override
    public String id() {
        return "H8";
    }

    @Override
    public Severity severity() {
        return Severity.LOW;
    }

    @Override
    public List<Finding> check(Subject subject) {
        if (!subject.isParsed()) {
            return List.of();
        }
        Lines lines = Lines.of(subject.file());
        Cursor cursor = new Cursor(null, subject.file());
        List<Copied> copied = new ArrayList<>();
        List<Finding> findings = new ArrayList<>();
        FinalImage.instructions(subject.file()).forEach(instruction -> {
            switch (instruction) {
                case Docker.Copy _, Docker.Add _ -> copied.addAll(
                        copied(Continuations.oneLine(instruction.printTrimmed(cursor)), lines.of(instruction)));
                case Docker.Run run -> RunScripts.commands(run).orElse(List.of()).forEach(command ->
                        removals(command, copied).forEach(message -> findings.add(
                                finding(subject, lines.of(run), message))));
                default -> {
                }
            }
        });
        return findings;
    }

    static List<Copied> copied(String line, int lineNumber) {
        String keyword = line.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        AddSources read = AddSources.of(line);
        if (keyword.equals("ADD") && read.sources().stream().anyMatch(PlainAdd::archive)) {
            return List.of();
        }
        List<String> landed = landed(read);
        return IntStream.range(0, landed.size())
                .mapToObj(i -> new Copied(landed.get(i), name(read.sources().get(i)), keyword, lineNumber)).toList();
    }

    static List<String> removals(List<String> command, List<Copied> copied) {
        return removed(command).stream().flatMap(path -> copied.stream()
                        .filter(copy -> path.equals(copy.path())
                                || recursive(command) && copy.path().startsWith(path + "/"))
                        .findFirst().map(copy -> removal(path, copy)).stream())
                .toList();
    }

    static String removal(String path, Copied copy) {
        return "rm " + path + " removes " + (copy.source().equals(".") ? copy.path() : copy.source()) + ", which "
                + copy.instruction() + " on line " + copy.line() + " wrote; its layer still holds it";
    }

    static List<String> landed(AddSources read) {
        String destination = read.destination();
        if (destination == null || !destination.startsWith("/") || destination.contains("$")) {
            return List.of();
        }
        boolean directory = destination.endsWith("/") || read.sources().size() > 1;
        String base = TrailingSlash.removed(destination);
        return read.sources().stream()
                .map(source -> directory && !name(source).equals(".") ? base + "/" + name(source) : base).toList();
    }

    static List<String> removed(List<String> command) {
        List<String> words = !command.isEmpty() && command.getFirst().equals("sudo")
                ? command.subList(1, command.size()) : command;
        return words.isEmpty() || !words.getFirst().equals("rm") ? List.of() : words.subList(1, words.size()).stream()
                .filter(word -> word.startsWith("/") && !word.contains("$") && !word.contains("*"))
                .map(TrailingSlash::removed).toList();
    }

    static boolean recursive(List<String> command) {
        return command.stream().anyMatch(word -> word.equals("--recursive")
                || word.startsWith("-") && !word.startsWith("--") && word.toLowerCase(Locale.ROOT).contains("r"));
    }

    static String name(String source) {
        String path = TrailingSlash.removed(source);
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
