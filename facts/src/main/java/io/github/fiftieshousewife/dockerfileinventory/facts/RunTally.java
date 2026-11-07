package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class RunTally {

    private final List<StagedCall> calls = new ArrayList<>();
    private int runs;
    private int opaque;
    private int otherShell;

    void add(int stage, Docker.Run run, Map<String, String> stageVariables, boolean underOtherShell) {
        if (underOtherShell) {
            otherShell++;
            return;
        }
        runs++;
        Optional<List<List<String>>> commands = RunScripts.commands(run);
        if (commands.isEmpty()) {
            opaque++;
        }
        Map<String, String> variables = new HashMap<>(stageVariables);
        RunScripts.script(run).ifPresent(script -> variables.putAll(ShellTokeniser.assignments(script)));
        commands.ifPresent(c -> PackageCalls.of(c, variables).forEach(call -> calls.add(new StagedCall(stage, call))));
    }

    List<StagedCall> calls() {
        return List.copyOf(calls);
    }

    int runs() {
        return runs;
    }

    int opaque() {
        return opaque;
    }

    int otherShell() {
        return otherShell;
    }
}
