package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.ArrayList;
import java.util.List;
import org.openrewrite.docker.tree.Docker;

/** H3: a shell-form {@code RUN} straight after another, which could be one layer. */
public final class AdjacentRuns implements Rule {

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
        List<Finding> findings = new ArrayList<>();
        for (Docker.Stage stage : subject.file().getStages()) {
            List<Docker.Instruction> instructions = stage.getInstructions();
            for (int i = 1; i < instructions.size(); i++) {
                if (combinable(instructions.get(i - 1)) && combinable(instructions.get(i))) {
                    findings.add(finding(subject, lines.of(instructions.get(i)),
                            "this RUN could join the RUN before it"));
                }
            }
        }
        return findings;
    }

    /** A shell-form {@code RUN} with no flags: a {@code --mount} or {@code --network} changes what joining means. */
    static boolean combinable(Docker.Instruction instruction) {
        if (!(instruction instanceof Docker.Run run) || !(run.getCommand() instanceof Docker.ShellForm)) {
            return false;
        }
        List<Docker.Flag> flags = run.getFlags();
        return flags == null || flags.isEmpty();
    }
}
