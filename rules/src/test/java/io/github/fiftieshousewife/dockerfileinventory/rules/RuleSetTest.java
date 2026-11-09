package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class RuleSetTest {

    private static final BaseList LIST = new BaseList(List.of());

    @Test
    void withoutListsOrLineageOnlyTheSelfContainedPerFileRulesRun() {
        RuleSet rules = RuleSet.of(null, null, false, false);

        assertAll(
                () -> assertThat(rules.perFile()).extracting(Rule::id)
                        .containsExactly("R1", "H1", "H2", "I1", "I2", "S2", "S3", "H3", "R3", "S5", "H4", "S6", "H5",
                                "H6", "H7", "H8", "S7", "H9"),
                () -> assertThat(rules.effective()).isEmpty(),
                () -> assertThat(rules.catalogue()).isEmpty(),
                () -> assertThat(rules.skipped()).containsExactly("S1", "R2", "S4", "R4"));
    }

    @Test
    void withEveryInputEveryRuleRunsAndNoneIsSkipped() {
        RuleSet rules = RuleSet.of(LIST, LIST, true, true);

        assertAll(
                () -> assertThat(rules.perFile()).extracting(Rule::id).endsWith("S1", "R2"),
                () -> assertThat(rules.effective()).extracting(EffectiveRule::id).containsExactly("S4", "S1"),
                () -> assertThat(rules.catalogue()).extracting(CatalogueRule::id).containsExactly("R4"),
                () -> assertThat(rules.skipped()).isEmpty());
    }

    @Test
    void lineageWithoutTheApprovedListRunsOnlyS4OverImages() {
        RuleSet rules = RuleSet.of(null, LIST, true, false);

        assertAll(
                () -> assertThat(rules.effective()).extracting(EffectiveRule::id).containsExactly("S4"),
                () -> assertThat(rules.skipped()).containsExactly("S1", "R4"));
    }

    @Test
    void theListedPerFileRulesRunOnlyWithTheirLists() {
        assertThat(RuleSet.perFile(null, LIST)).extracting(Rule::id).endsWith("H9", "R2");
    }

    @Test
    void theEffectiveRulesRunOnlyWithLineage() {
        assertThat(RuleSet.effective(LIST, false)).isEmpty();
    }

    @Test
    void eachRuleWithoutItsInputIsSkipped() {
        assertThat(RuleSet.skipped(LIST, null, true, false)).containsExactly("R2", "R4");
    }
}
