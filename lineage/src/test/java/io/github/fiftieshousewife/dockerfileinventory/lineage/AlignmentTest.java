package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static io.github.fiftieshousewife.dockerfileinventory.lineage.LinksTest.dockerfile;
import static io.github.fiftieshousewife.dockerfileinventory.lineage.LinksTest.instruction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class AlignmentTest {

    @Test
    void labelsCountAsOneStepWhereverTheyAreMatchedWhenTheImageHoldsEveryKeyTheDockerfileSets() {
        List<Instruction> image = List.of(instruction("COPY /usr/bin/"), instruction("ENTRYPOINT [\"/usr/bin/x\"]"),
                instruction("LABEL description name vendor version"));

        assertThat(Alignment.score(image, List.of(instruction("LABEL description"), instruction("COPY /usr/bin/"),
                instruction("ENTRYPOINT [\"/usr/bin/x\"]")))).isEqualTo(1.0);
        assertThat(Alignment.score(image, List.of(instruction("COPY /usr/bin/"),
                instruction("ENTRYPOINT [\"/usr/bin/x\"]")))).isEqualTo(1.0);
        assertThat(Alignment.score(image, List.of(instruction("LABEL io.openshift.release.operator"),
                instruction("COPY /usr/bin/"), instruction("ENTRYPOINT [\"/usr/bin/x\"]")))).isEqualTo(2.0 / 3);
    }

    @Test
    void aLabelStepAlignsWithADockerfileLabelWhoseKeysItHoldsAsBuildToolingAddsKeys() {
        assertThat(Alignment.score(
                List.of(instruction("LABEL io.k8s.display-name name vendor version"), instruction("USER 0")),
                List.of(instruction("LABEL io.k8s.display-name"), instruction("USER 0")))).isEqualTo(1.0);
        assertThat(Alignment.score(List.of(instruction("LABEL name")),
                List.of(instruction("LABEL io.k8s.display-name"))))
                .isZero();
    }

    @Test
    void twoEmptyStepListsScoreZero() {
        assertThat(Alignment.score(List.of(), List.of())).isZero();
    }

    @Test
    void theCommonSubsequenceCountsStepsMatchedInOrder() {
        assertThat(Alignment.commonSubsequence(
                List.of(instruction("RUN a"), instruction("RUN b"), instruction("RUN c")),
                List.of(instruction("RUN a"), instruction("RUN x"), instruction("RUN c"), instruction("RUN b"))))
                .isEqualTo(2);
    }

    @Test
    void labelStepsAreSetAsideAndTheirKeysCollected() {
        List<Instruction> steps = List.of(instruction("LABEL a b"), instruction("RUN make"), instruction("LABEL c"));

        assertThat(Alignment.withoutLabels(steps)).containsExactly(instruction("RUN make"));
        assertThat(Alignment.labelKeys(steps)).containsExactlyInAnyOrder("a", "b", "c");
    }

    private static final List<Instruction> OWN = List.of(instruction("RUN a"), instruction("RUN b"),
            instruction("RUN c"), instruction("RUN d"), instruction("RUN e"));

    @Test
    void theBestAlignedCandidateIsAWeakLinkCarryingItsScoreAndTheRunnerUps() {
        assertThat(Alignment.best(OWN, List.of(
                dockerfile("near", "x", false, "RUN a", "RUN b", "RUN c", "RUN d"),
                dockerfile("poor", "x", false, "RUN a"))))
                .isEqualTo(new Link(Link.Tier.WEAK, List.of("near"), false, 0.8, 0.2));
    }

    @Test
    void identicalDockerfilesAreOneCandidateWithNoRunnerUp() {
        assertThat(Alignment.best(OWN, List.of(
                dockerfile("near", "x", false, "RUN a", "RUN b", "RUN c", "RUN d"),
                dockerfile("copy", "x", false, "RUN a", "RUN b", "RUN c", "RUN d"))))
                .isEqualTo(new Link(Link.Tier.WEAK, List.of("copy", "near"), false, 0.8, null));
    }

    @Test
    void differentDockerfilesScoringTheSameAreATieWithNoMargin() {
        assertThat(Alignment.best(OWN, List.of(
                dockerfile("abcd", "x", false, "RUN a", "RUN b", "RUN c", "RUN d"),
                dockerfile("bcde", "x", false, "RUN b", "RUN c", "RUN d", "RUN e"))))
                .isEqualTo(new Link(Link.Tier.WEAK, List.of("abcd", "bcde"), false, 0.8, 0.8));
    }

    @Test
    void aBestScoreBelowTheMinimumOrNoCandidateIsNoLink() {
        assertAll(
                () -> assertThat(Alignment.best(OWN, List.of(dockerfile("poor", "x", false, "RUN a"))))
                        .isEqualTo(Link.NONE),
                () -> assertThat(Alignment.best(OWN, List.of())).isEqualTo(Link.NONE));
    }
}
