package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RepoOutcomeTest {

    @Test
    void onlyAFailedRepositoryIsTriedAgain() {
        assertThat(Arrays.stream(RepoOutcome.Status.values()).filter(s -> !s.isFinal()))
                .containsExactly(RepoOutcome.Status.FAILED);
    }
}
