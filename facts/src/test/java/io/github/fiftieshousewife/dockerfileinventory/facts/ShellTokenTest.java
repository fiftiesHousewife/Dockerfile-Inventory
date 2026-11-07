package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.facts.ShellToken.Word;
import org.junit.jupiter.api.Test;

class ShellTokenTest {

    @Test
    void aWordsTextRestoresMarkedDollars() {
        assertThat(new Word("a" + ShellToken.LITERAL_DOLLAR + "B", true).text()).isEqualTo("a$B");
    }
}
