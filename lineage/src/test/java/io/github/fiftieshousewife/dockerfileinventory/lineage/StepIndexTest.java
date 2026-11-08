package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static io.github.fiftieshousewife.dockerfileinventory.lineage.LinksTest.dockerfile;
import static io.github.fiftieshousewife.dockerfileinventory.lineage.LinksTest.instruction;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StepIndexTest {

    @Test
    void anImagesCandidatesAreTheDockerfilesSharingOneOfItsSteps() {
        StepIndex index = new StepIndex(List.of(
                dockerfile("proxy", "ocp/5.0", false, "COPY /usr/bin/proxy-agent", "USER 0"),
                dockerfile("other", "ocp/5.0", false, "COPY /usr/bin/other", "USER 0")));

        assertThat(index.candidates(List.of(instruction("COPY /usr/bin/proxy-agent"))))
                .extracting(Dockerfile::blobSha).containsExactly("proxy");
    }

    @Test
    void aStepInMoreThanTheRareLimitOfDockerfilesBringsNoCandidates() {
        List<Dockerfile> many = new ArrayList<>(IntStream.rangeClosed(0, StepIndex.RARE)
                .mapToObj(i -> dockerfile("d" + i, "ubi9", false, "USER 1001", "COPY /app" + i)).toList());

        assertThat(new StepIndex(many).candidates(List.of(instruction("USER 1001")))).isEmpty();
    }

    @Test
    void labelStepsBringNoCandidatesSinceTheAlignmentReadsTheirKeysApart() {
        StepIndex index = new StepIndex(List.of(dockerfile("labelled", "ubi9", false, "LABEL name")));

        assertThat(index.candidates(List.of(instruction("LABEL name")))).isEmpty();
    }

    @Test
    void aDockerfileSharingSeveralStepsIsOneCandidate() {
        StepIndex index = new StepIndex(List.of(dockerfile("both", "ubi9", false, "COPY /a", "COPY /b")));

        assertThat(index.candidates(List.of(instruction("COPY /a"), instruction("COPY /b")))).hasSize(1);
    }
}
