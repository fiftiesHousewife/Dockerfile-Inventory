package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.Comparator;
import java.util.List;

// Worst first.
enum Verdict {
    REFUSED, BREAKS, DRIFT, INFORMATION, RUNS;

    static Verdict of(List<Finding> findings) {
        return findings.stream().map(Finding::severity).min(Comparator.naturalOrder())
                .map(severity -> Verdict.valueOf(severity.name())).orElse(RUNS);
    }
}
