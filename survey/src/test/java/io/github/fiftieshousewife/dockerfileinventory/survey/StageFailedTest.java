package io.github.fiftieshousewife.dockerfileinventory.survey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StageFailedTest {

    @Test
    void theFailureNamesTheStageAndTheFirstLineOfWhy() {
        assertThat(new StageFailed("discovery", new IllegalStateException("returned 500: {\n  \"message\"")))
                .hasMessage("discovery failed: returned 500: {");
    }

    @Test
    void aRefusedTokenIsExplainedWithHowToGiveAnother() {
        assertThat(new StageFailed("discovery",
                new IllegalStateException("POST https://api.github.com/graphql returned 401: {")).explained())
                .contains("GitHub refused the token", "GHE_TOKEN_FILE", "env -u GITHUB_TOKEN gh auth token");
    }

    @Test
    void anyOtherFailureIsSaidAsItIs() {
        assertThat(new StageFailed("facts", new IllegalStateException("no network")).explained())
                .isEqualTo("facts failed: no network");
    }

    @Test
    void aStageFailingWithoutAMessageIsNamedByTheFailuresKind() {
        assertThat(new StageFailed("discovery", new NullPointerException()).getMessage())
                .isEqualTo("discovery failed: NullPointerException");
    }
}
