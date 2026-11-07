package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.BaseImage;
import io.github.fiftieshousewife.dockerfileinventory.facts.NormalForms;
import java.util.List;

final class UnapprovedBase implements Rule {

    private final BaseList list;

    UnapprovedBase(BaseList list) {
        this.list = list;
    }

    @Override
    public String id() {
        return "S1";
    }

    @Override
    public Severity severity() {
        return Severity.HIGH;
    }

    @Override
    public List<Finding> check(Subject subject) {
        if (!subject.isParsed()) {
            return List.of();
        }
        BaseImage base = subject.facts().base();
        if (!base.resolved()) {
            return List.of(finding(subject, null, "the base is set by a build argument and cannot be checked"));
        }
        return list.lookup(NormalForms.fromPath(base), base.tag()).isPresent() ? List.of()
                : List.of(finding(subject, null, "base " + Bases.reference(base) + " is not on the approved list"));
    }
}
