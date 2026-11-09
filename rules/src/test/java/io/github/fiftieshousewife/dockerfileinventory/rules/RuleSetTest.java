package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuleSetTest {

    private static final BaseList LIST = new BaseList(List.of());

    @Test
    void withoutListsOrLineageOnlyTheSelfContainedPerFileRulesRun() {
        assertThat(RuleSet.of(null, null, false)).satisfies(rules -> {
            assertThat(rules.perFile()).extracting(Rule::id)
                    .containsExactly("R1", "H1", "H2", "I1", "I2", "S2", "S3", "H3", "R3", "S5", "H4", "S6", "H5",
                            "H6", "H7");
            assertThat(rules.effective()).isEmpty();
            assertThat(rules.skipped()).containsExactly("S1", "R2", "S4");
        });
    }

    @Test
    void withEveryInputEveryRuleRunsAndNoneIsSkipped() {
        assertThat(RuleSet.of(LIST, LIST, true)).satisfies(rules -> {
            assertThat(rules.perFile()).extracting(Rule::id).endsWith("S1", "R2");
            assertThat(rules.effective()).extracting(EffectiveRule::id).containsExactly("S4", "S1");
            assertThat(rules.skipped()).isEmpty();
        });
    }

    @Test
    void lineageWithoutTheApprovedListRunsOnlyS4OverImages() {
        assertThat(RuleSet.of(null, LIST, true)).satisfies(rules -> {
            assertThat(rules.effective()).extracting(EffectiveRule::id).containsExactly("S4");
            assertThat(rules.skipped()).containsExactly("S1");
        });
    }
}
