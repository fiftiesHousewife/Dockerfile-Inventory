package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;

interface Rule {

    String id();

    Severity severity();

    List<Finding> check(Subject subject);

    default Finding finding(Subject subject, Integer line, String message) {
        return new Finding(id(), severity(), subject.blobSha(), line, message, null, List.of());
    }
}
