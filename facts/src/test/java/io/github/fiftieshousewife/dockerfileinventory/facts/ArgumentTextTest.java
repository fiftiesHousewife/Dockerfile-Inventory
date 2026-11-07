package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

class ArgumentTextTest {

    @Test
    void substitutesAKnownVariable() {
        Docker.User user = Parsed.first("FROM ubi9\nUSER $UID\n", Docker.User.class);

        assertThat(ArgumentText.of(user.getUser(), Map.of("UID", "1001"))).isEqualTo("1001");
    }

    @Test
    void writesAnUnknownVariableInBraces() {
        Docker.User user = Parsed.first("FROM ubi9\nUSER app-$UID\n", Docker.User.class);

        assertThat(ArgumentText.of(user.getUser(), Map.of())).isEqualTo("app-${UID}");
    }
}
