package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LinkRuleTest {

    private static final LinkRule RULE = new LinkRule(0.8, 0.05);

    @Test
    void aStrongLinkOrAProbableOneToASingleDockerfileIsCredited() {
        assertAll(
                () -> assertThat(RULE.credits(new Link(Link.Tier.STRONG, List.of("a", "b"), false))).isTrue(),
                () -> assertThat(RULE.credits(new Link(Link.Tier.PROBABLE, List.of("a"), false))).isTrue());
    }

    @Test
    void aWeakLinkToOneDockerfileReachingTheThresholdAndBeatingTheRunnerUpByTheMarginIsCredited() {
        assertAll(
                () -> assertThat(RULE.credits(weak(0.8, 0.75))).isTrue(),
                () -> assertThat(RULE.credits(weak(0.9, null))).isTrue());
    }

    @Test
    void aLinkBelowTheThresholdWithinTheMarginToSeveralDockerfilesOrToNoneIsNotCredited() {
        assertAll(
                () -> assertThat(RULE.credits(weak(0.79, null))).isFalse(),
                () -> assertThat(RULE.credits(weak(0.9, 0.86))).isFalse(),
                () -> assertThat(RULE.credits(new Link(Link.Tier.PROBABLE, List.of("a", "b"), false))).isFalse(),
                () -> assertThat(RULE.credits(new Link(Link.Tier.WEAK, List.of("a", "b"), false, 0.9, 0.9)))
                        .isFalse(),
                () -> assertThat(RULE.credits(Link.NONE)).isFalse());
    }

    @Test
    void creditingMarksEachLinkWithTheRulesDecision() {
        Map<String, Link> credited = RULE.credit(Map.of("exact", new Link(Link.Tier.PROBABLE, List.of("a"), false),
                "close", weak(0.9, 0.86)));

        assertAll(
                () -> assertThat(credited.get("exact").credited()).isTrue(),
                () -> assertThat(credited.get("close").credited()).isFalse());
    }

    private static Link weak(double score, Double runnerUp) {
        return new Link(Link.Tier.WEAK, List.of("a"), false, score, runnerUp);
    }
}
