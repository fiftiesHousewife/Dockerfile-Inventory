package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class LocatedTest {

    @Test
    void keepsTheBlobShaAsTheIdentity() {
        Located located = new Located("org/repo", "abc123", Scope.RELEASED, "docker/Dockerfile", "f00d");

        assertThat(located.blobSha()).isEqualTo("f00d");
    }

    @Test
    void refusesAMissingBlobSha() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Located("org/repo", "abc123", Scope.RELEASED, "Dockerfile", null))
                .withMessage("blobSha");
    }
}
