package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class OrderedMapTest {

    @Test
    void pairsKeepTheOrderGivenAndCannotChange() {
        assertThat(OrderedMap.of("timeout", "3s", "interval", "30s", "retries", "3"))
                .satisfies(map -> assertThat(map.keySet()).containsExactly("timeout", "interval", "retries"),
                        map -> assertThat(map).isUnmodifiable());
    }

    @Test
    void aKeyWithoutAValueIsRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> OrderedMap.of("timeout", "3s", "interval"));
    }
}
