package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.BaseImage;
import java.util.List;

final class UnpinnedBase implements Rule {

    @Override
    public String id() {
        return "R1";
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public List<Finding> check(Subject subject) {
        if (!subject.isParsed()) {
            return List.of();
        }
        BaseImage base = subject.facts().base();
        if (!base.resolved() || base.digestPinned() || base.name().equals("scratch")) {
            return List.of();
        }
        return List.of(finding(subject, null, "base " + Bases.reference(base) + " is not pinned by digest"));
    }
}
