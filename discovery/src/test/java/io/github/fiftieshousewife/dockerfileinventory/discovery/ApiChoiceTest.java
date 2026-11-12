package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiChoiceTest {

    @Test
    void restIsChosenOnlyWhenAskedFor() {
        assertAll(
                () -> assertThat(ApiChoice.restOnly(Map.of())).isFalse(),
                () -> assertThat(ApiChoice.restOnly(Map.of("GHE_API", " auto "))).isFalse(),
                () -> assertThat(ApiChoice.restOnly(Map.of("GHE_API", "rest"))).isTrue(),
                () -> assertThatThrownBy(() -> ApiChoice.restOnly(Map.of("GHE_API", "graphql")))
                        .hasMessage("GHE_API must be auto or rest, not graphql"));
    }

    @Test
    void lastChangesOverRestAreReadOnlyWhenAskedFor() {
        assertAll(
                () -> assertThat(ApiChoice.lastChanges(Map.of())).isFalse(),
                () -> assertThat(ApiChoice.lastChanges(Map.of("GHE_LAST_CHANGES", "true"))).isTrue(),
                () -> assertThat(ApiChoice.lastChanges(Map.of("GHE_LAST_CHANGES", " false "))).isFalse(),
                () -> assertThatThrownBy(() -> ApiChoice.lastChanges(Map.of("GHE_LAST_CHANGES", "yes")))
                        .hasMessage("GHE_LAST_CHANGES must be true or false, not yes"));
    }
}
