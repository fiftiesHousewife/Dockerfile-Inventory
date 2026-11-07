package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;

record Finding(String rule, Severity severity, String blobSha, Integer line, String message, String valueHash,
               List<String> contributing) {

    Finding {
        contributing = List.copyOf(contributing);
    }
}
