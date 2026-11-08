package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.Comparator;
import java.util.List;

// What an image's findings mean for it under a profile, worst first.
enum Verdict {
    BREAKS, DRIFT, INFORMATION, RUNS;

    static Verdict of(List<Finding> findings) {
        return findings.stream().map(Finding::severity).min(Comparator.naturalOrder())
                .map(severity -> Verdict.valueOf(severity.name())).orElse(RUNS);
    }
}
