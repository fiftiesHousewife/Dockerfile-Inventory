package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.BaseImage;
import io.github.fiftieshousewife.dockerfileinventory.facts.NormalForms;
import java.util.List;

/** R2: the final base is past end of life by the internal list (D2). */
public final class EndOfLifeBase implements Rule {

    private final BaseList list;

    public EndOfLifeBase(BaseList list) {
        this.list = list;
    }

    @Override
    public String id() {
        return "R2";
    }

    @Override
    public Severity severity() {
        return Severity.MEDIUM;
    }

    @Override
    public List<Finding> check(Subject subject) {
        if (!subject.isParsed() || !subject.facts().base().resolved()) {
            return List.of();
        }
        BaseImage base = subject.facts().base();
        return list.lookup(NormalForms.fromPath(base), base.tag())
                .filter(entry -> "eol".equalsIgnoreCase(entry.status()))
                .map(entry -> List.of(finding(subject, null, "base %s reached end of life on %s"
                        .formatted(Bases.reference(base), entry.eolDate()))))
                .orElse(List.of());
    }
}
