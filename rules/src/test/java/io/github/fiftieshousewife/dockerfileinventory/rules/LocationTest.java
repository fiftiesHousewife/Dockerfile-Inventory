package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class LocationTest {

    @Test
    void theOrganisationIsTheRepositorysOwner() {
        assertAll(
                () -> assertThat(new Location("payments/api/v2", "Dockerfile").organisation()).isEqualTo("payments"),
                () -> assertThat(new Location("standalone", "Dockerfile").organisation()).isEqualTo("standalone"));
    }
}
