package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocationTest {

    @Test
    void theOrganisationIsTheRepositorysFirstSegment() {
        assertThat(new Location("konflux-ci/community-operators-prod", "Dockerfile", "RELEASED").organisation())
                .isEqualTo("konflux-ci");
        assertThat(new Location("standalone", "Dockerfile", "RELEASED").organisation()).isEqualTo("standalone");
    }
}
