package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class AddSourcesTest {

    @Test
    void anAddsFlagsAndSourcesAreReadInEitherFormWithoutItsDestination() {
        assertAll(
                () -> assertThat(AddSources.of("ADD --chown=1:0 --checksum=sha256:ab a.jar \"b c\" /opt/"))
                        .isEqualTo(new AddSources(List.of("--chown=1:0", "--checksum=sha256:ab"),
                                List.of("a.jar", "b c"))),
                () -> assertThat(AddSources.of("ADD [\"a.jar\", \"/opt/\"]").sources()).containsExactly("a.jar"),
                () -> assertThat(AddSources.of("ADD only").sources()).isEmpty());
    }

    @Test
    void aRemoteSourceIsAnHttpOrGitUrl() {
        assertAll(
                () -> assertThat(AddSources.remote("https://x/a")).isTrue(),
                () -> assertThat(AddSources.remote("git@github.com:a/b.git")).isTrue(),
                () -> assertThat(AddSources.remote("http.txt")).isFalse());
    }
}
