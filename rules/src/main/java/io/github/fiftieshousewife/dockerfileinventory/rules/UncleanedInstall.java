package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;
import java.util.Set;

/** H1: an RPM install whose cache stays in the layer because no clean follows in the same {@code RUN}. */
public final class UncleanedInstall implements Rule {

    private static final Set<String> RPM = Set.of("dnf", "yum", "microdnf");
    private static final Set<String> INSTALLS = Set.of("install", "reinstall");

    @Override
    public String id() {
        return "H1";
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
        return subject.facts().packageCalls().stream()
                .filter(staged -> RPM.contains(staged.call().manager()))
                .filter(staged -> INSTALLS.contains(staged.call().subcommand()))
                .filter(staged -> !staged.call().cleanedInSameRun())
                .map(staged -> finding(subject, null, "%s %s in stage %d has no clean in the same RUN"
                        .formatted(staged.call().manager(), staged.call().subcommand(), staged.stage())))
                .toList();
    }
}
