package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfile.reader.internal.Continuations;
import io.github.fiftieshousewife.dockerfileinventory.facts.RunScripts;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        for (Docker.Instruction instruction : FinalImage.instructions(subject.file())) {
            if (instruction instanceof Docker.Copy || instruction instanceof Docker.Add) {
                String line = Continuations.oneLine(instruction.printTrimmed(cursor));
                String keyword = line.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
                AddSources read = AddSources.of(line);
                if (!(keyword.equals("ADD") && read.sources().stream().anyMatch(PlainAdd::archive))) {
                    List<String> landed = landed(read);
                    for (int i = 0; i < landed.size(); i++) {
                        copied.add(new Copied(landed.get(i), name(read.sources().get(i)), keyword,
                                lines.of(instruction)));
                    }
                }
            } else if (instruction instanceof Docker.Run run) {
                RunScripts.commands(run).orElse(List.of()).forEach(command -> removed(command).forEach(path ->
                        copied.stream().filter(copy -> path.equals(copy.path())
                                        || recursive(command) && copy.path().startsWith(path + "/"))
                                .findFirst().ifPresent(copy -> findings.add(finding(subject, lines.of(run),
                                        "rm " + path + " removes " + (copy.source().equals(".") ? copy.path()
                                                : copy.source()) + ", which " + copy.instruction()
                                                + " on line " + copy.line() + " wrote; its layer still holds it")))));
            }
        }
        return findings;
    }

    // Where each source lands: at the destination, or inside it when it is a directory or there are several.
    static List<String> landed(AddSources read) {
        String destination = read.destination();
        if (destination == null || !destination.startsWith("/") || destination.contains("$")) {
            return List.of();
        }
        boolean directory = destination.endsWith("/") || read.sources().size() > 1;
        String base = trimmed(destination);
        return read.sources().stream()
                .map(source -> directory && !name(source).equals(".") ? base + "/" + name(source) : base).toList();
    }

    static List<String> removed(List<String> command) {
        List<String> words = !command.isEmpty() && command.getFirst().equals("sudo")
                ? command.subList(1, command.size()) : command;
        return words.isEmpty() || !words.getFirst().equals("rm") ? List.of() : words.subList(1, words.size()).stream()
                .filter(word -> word.startsWith("/") && !word.contains("$") && !word.contains("*"))
                .map(RemovedLater::trimmed).toList();
    }

    private static boolean recursive(List<String> command) {
        return command.stream().anyMatch(word -> word.equals("--recursive")
                || word.startsWith("-") && !word.startsWith("--") && word.toLowerCase(Locale.ROOT).contains("r"));
    }

    private static String name(String source) {
        String path = trimmed(source);
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String trimmed(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
