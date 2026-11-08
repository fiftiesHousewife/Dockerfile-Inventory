package io.github.fiftieshousewife.dockerfile.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class RunMerge {

    private static final Set<String> STATEFUL = Set.of("cd", "export", "source", ".", "set", "umask");

    private RunMerge() {
    }

    static List<Instruction> merged(List<Instruction> instructions) {
        List<Instruction> merged = new ArrayList<>();
        instructions.forEach(instruction -> {
            if (!merged.isEmpty() && merged.getLast() instanceof Run previous
                    && instruction instanceof Run next && joinable(previous, next)) {
                merged.set(merged.size() - 1, joined(previous, next));
            } else {
                merged.add(instruction);
            }
        });
        return merged;
    }

    static boolean joinable(Run previous, Run next) {
        return previous.flags().isEmpty() && next.flags().isEmpty() && RunCommands.joins(previous.body())
                && RunCommands.joins(next.body()) && !changesShellState(previous);
    }

    static Run joined(Run first, Run second) {
        List<String> commands = new ArrayList<>(RunCommands.of(first.body()));
        commands.addAll(RunCommands.of(second.body()));
        return new Run(new RunBody.Shell(commands));
    }

    // Joining would carry a cd or export into commands that never saw it.
    static boolean changesShellState(Run run) {
        return RunCommands.of(run.body()).stream()
                .flatMap(command -> Arrays.stream(command.split("&&|;|\\|\\|")))
                .map(String::strip)
                .map(command -> command.split("\\s+", 2)[0])
                .anyMatch(STATEFUL::contains);
    }
}
