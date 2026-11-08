package io.github.fiftieshousewife.dockerfile.reader.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfile.reader.internal.ShellToken.Word;
import org.junit.jupiter.api.Test;

class ShellTokenTest {

    @Test
    void aWordsTextRestoresMarkedDollars() {
        assertThat(new Word("a" + ShellToken.LITERAL_DOLLAR + "B", true).text()).isEqualTo("a$B");
    }
}
