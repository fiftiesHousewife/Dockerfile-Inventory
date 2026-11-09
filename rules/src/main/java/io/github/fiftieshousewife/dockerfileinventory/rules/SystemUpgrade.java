package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import io.github.fiftieshousewife.dockerfileinventory.facts.RunScripts;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class SystemUpgrade implements Rule {

    // apt's and apk's update only refreshes the index; the RPM managers' update upgrades.
    private static final Map<String, Set<String>> UPGRADES = Map.of(
            "apt-get", Set.of("upgrade", "dist-upgrade", "full-upgrade"),
            "apt", Set.of("upgrade", "dist-upgrade", "full-upgrade"),
            "apk", Set.of("upgrade"),
            "dnf", Set.of("update", "upgrade", "distro-sync", "update-minimal", "upgrade-minimal"),
            "yum", Set.of("update", "upgrade", "distro-sync", "update-minimal", "upgrade-minimal"),
            "microdnf", Set.of("update", "upgrade"),
            "zypper", Set.of("update", "up", "dist-upgrade", "dup", "patch"));

    @Override
    public String id() {
        return "H4";
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
        return subject.file().getStages().stream().flatMap(stage -> stage.getInstructions().stream())
                .filter(i -> i instanceof Docker.Run).map(Docker.Run.class::cast)
                .flatMap(run -> RunScripts.commands(run).orElse(List.of()).stream()
                        .flatMap(words -> wholeSystemUpgrade(words).stream())
                        .map(upgrade -> finding(subject, lines.of(run), upgrade + " upgrades every package the base "
                                + "has")))
                .toList();
    }

    static Optional<String> wholeSystemUpgrade(List<String> command) {
        List<String> words = !command.isEmpty() && command.getFirst().equals("sudo")
                ? command.subList(1, command.size()) : command;
        if (words.isEmpty() || !UPGRADES.containsKey(words.getFirst())) {
            return Optional.empty();
        }
        List<String> rest = words.subList(1, words.size()).stream().filter(w -> !w.startsWith("-")).toList();
        return !rest.isEmpty() && UPGRADES.get(words.getFirst()).contains(rest.getFirst()) && rest.size() == 1
                ? Optional.of(words.getFirst() + " " + rest.getFirst()) : Optional.empty();
    }
}
