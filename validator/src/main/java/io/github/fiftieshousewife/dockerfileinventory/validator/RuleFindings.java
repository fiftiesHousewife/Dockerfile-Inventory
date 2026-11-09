package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

// The rules stage's findings as the validator's: each read from one Dockerfile, its severity from the profile.
final class RuleFindings {

    private RuleFindings() {
    }

    static List<Finding> read(Path rulesDir, Profile profile) {
        return JsonLines.read(rulesDir.resolve("findings.jsonl")).stream().map(row -> {
            Check rule = rule(row.path("rule").asString());
            return new Finding(rule, profile.severity().get(rule), Certainty.READ, row.path("blobSha").asString(),
                    row.path("message").asString());
        }).filter(finding -> finding.severity() != Severity.OFF).toList();
    }

    static Check rule(String id) {
        return Arrays.stream(Check.values()).filter(c -> c.rule() && c.name().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("rule " + id
                        + " in the rules' findings.jsonl is not one the validator knows: add it to Check"));
    }
}
