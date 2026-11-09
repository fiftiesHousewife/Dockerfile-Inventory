package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleFindingsTest {

    @TempDir
    Path rules;

    @Test
    void eachRuleFindingIsAReadFindingWithTheProfilesSeverityAndThoseTurnedOffAreDropped() throws IOException {
        Files.writeString(rules.resolve("findings.jsonl"), """
                {"rule":"S2","severity":"CRITICAL","blobSha":"aaa","message":"ENV TOKEN holds a secret"}
                {"rule":"R1","severity":"MEDIUM","blobSha":"aaa","message":"base ubi9 is not pinned by digest"}
                """);

        assertThat(RuleFindings.read(rules, Fixtures.V2)).containsExactly(
                new Finding(Check.S2, Severity.DRIFT, Certainty.READ, "aaa", "ENV TOKEN holds a secret"));
    }

    @Test
    void aRuleTheValidatorDoesNotKnowIsRefusedByName() throws IOException {
        Files.writeString(rules.resolve("findings.jsonl"), """
                {"rule":"L9","severity":"LOW","blobSha":"aaa","message":"new"}
                """);

        assertThatThrownBy(() -> RuleFindings.read(rules, Fixtures.V2))
                .hasMessage("rule L9 in the rules' findings.jsonl is not one the validator knows: add it to Check");
    }
}
