package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.stream.IntStream;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class AdjacentRuns implements Rule {

    @Override
    public String id() {
        return "H3";
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
        return subject.file().getStages().stream()
                .flatMap(stage -> joinable(stage.getInstructions()).stream())
                .map(run -> finding(subject, lines.of(run), "this RUN could join the RUN before it"))
                .toList();
    }

    static List<Docker.Instruction> joinable(List<Docker.Instruction> instructions) {
        return IntStream.range(1, instructions.size())
                .filter(i -> combinable(instructions.get(i - 1)) && combinable(instructions.get(i)))
                .mapToObj(instructions::get)
                .toList();
    }

    static boolean combinable(Docker.Instruction instruction) {
        if (!(instruction instanceof Docker.Run run) || !(run.getCommand() instanceof Docker.ShellForm)) {
            return false;
        }
        List<Docker.Flag> flags = run.getFlags();
        return flags == null || flags.isEmpty();
    }
}
