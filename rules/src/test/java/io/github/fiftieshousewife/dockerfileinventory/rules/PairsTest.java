package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PairsTest {

    @Test
    void aSingleQuotedValueIsKeptWhole() {
        assertThat(Pairs.keys("INSTALL='docker run -v /:/host $IMAGE' name=x")).containsExactly("INSTALL", "name");
    }
}
