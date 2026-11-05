package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class LocatedTest {

    @Test
    void keepsTheBlobShaAsTheIdentity() {
        Located located = new Located("org/repo", "v1.2", "abc123", "docker/Dockerfile", "f00d");

        assertThat(located.blobSha()).isEqualTo("f00d");
    }

    @Test
    void refusesAMissingBlobSha() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Located("org/repo", "main", "abc123", "Dockerfile", null))
                .withMessage("blobSha");
    }
}
