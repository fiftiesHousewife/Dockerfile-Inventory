package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class TrailingSlashTest {

    @Test
    void aTrailingSlashIsRemovedExceptFromTheRoot() {
        assertAll(
                () -> assertThat(TrailingSlash.removed("/opt/app/")).isEqualTo("/opt/app"),
                () -> assertThat(TrailingSlash.removed("/opt/app")).isEqualTo("/opt/app"),
                () -> assertThat(TrailingSlash.removed("/")).isEqualTo("/"));
    }
}
