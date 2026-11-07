package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.EntrypointForm;
import java.util.List;

final class ShellEntrypoint implements Rule {

    @Override
    public String id() {
        return "H2";
    }

    @Override
    public Severity severity() {
        return Severity.LOW;
    }

    @Override
    public List<Finding> check(Subject subject) {
        return subject.isParsed() && subject.facts().entrypointForm() == EntrypointForm.SHELL
                ? List.of(finding(subject, null, "the final ENTRYPOINT is in shell form"))
                : List.of();
    }
}
