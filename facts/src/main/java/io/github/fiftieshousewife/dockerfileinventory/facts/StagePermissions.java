package io.github.fiftieshousewife.dockerfileinventory.facts;

import io.github.fiftieshousewife.dockerfile.reader.internal.ShellTokeniser;
import java.util.List;
import java.util.Set;

final class StagePermissions {

    private static final Set<String> COMMANDS = Set.of("chmod", "chown", "chgrp");

    private StagePermissions() {
    }

    static List<PermissionChange> of(String script) {
        return ShellTokeniser.parse(script).orElse(List.of()).stream()
                .map(words -> !words.isEmpty() && words.getFirst().equals("sudo") ? words.subList(1, words.size())
                        : words)
                .filter(words -> !words.isEmpty() && COMMANDS.contains(words.getFirst()))
                .map(StagePermissions::change)
                .filter(change -> change.setting() != null)
                .toList();
    }

    static PermissionChange change(List<String> words) {
        List<String> options = words.subList(1, words.size()).stream().takeWhile(w -> w.startsWith("-")).toList();
        List<String> rest = words.subList(1 + options.size(), words.size());
        boolean recursive = options.stream().anyMatch(o -> o.equals("--recursive")
                || !o.startsWith("--") && o.contains("R"));
        return new PermissionChange(words.getFirst(), rest.isEmpty() ? null : rest.getFirst(),
                rest.isEmpty() ? List.of() : rest.subList(1, rest.size()), recursive);
    }
}
