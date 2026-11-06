package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;

/** A rule from the catalogue. Detect-only: it reports, it never changes a file. */
public interface Rule {

    String id();

    Severity severity();

    List<Finding> check(Subject subject);

    default Finding finding(Subject subject, Integer line, String message) {
        return new Finding(id(), severity(), subject.blobSha(), line, message, null, java.util.List.of());
    }
}
