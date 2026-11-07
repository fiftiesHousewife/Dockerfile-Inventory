package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static io.github.fiftieshousewife.dockerfileinventory.lineage.LinksTest.dockerfile;
import static io.github.fiftieshousewife.dockerfileinventory.lineage.LinksTest.instruction;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
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

    @Test
    void theBestAlignedCandidatesAreAWeakLinkWhenTheyReachTheMinimum() {
        List<Instruction> own = List.of(instruction("RUN a"), instruction("RUN b"), instruction("RUN c"),
                instruction("RUN d"), instruction("RUN e"));

        assertThat(Alignment.best(own, List.of(
                dockerfile("near", "x", false, "RUN a", "RUN b", "RUN c", "RUN d"),
                dockerfile("poor", "x", false, "RUN a"))))
                .isEqualTo(new Link(Link.Tier.WEAK, List.of("near"), false));
    }

    @Test
    void tiesAreKeptSortedAndAScoreBelowTheMinimumIsNoLink() {
        assertThat(Alignment.tiedForBest(Map.of("b", 0.9, "a", 0.9, "c", 0.85)))
                .isEqualTo(new Link(Link.Tier.WEAK, List.of("a", "b"), false));
        assertThat(Alignment.tiedForBest(Map.of("a", 0.79))).isEqualTo(Link.NONE);
        assertThat(Alignment.tiedForBest(Map.of())).isEqualTo(Link.NONE);
    }
}
