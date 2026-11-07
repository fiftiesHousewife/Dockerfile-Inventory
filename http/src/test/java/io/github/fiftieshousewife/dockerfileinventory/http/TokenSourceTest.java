package io.github.fiftieshousewife.dockerfileinventory.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenSourceTest {

    @Test
    void aVariableGivesItsTokenAndRefreshingChangesNothing() {
        TokenSource source = TokenSource.from(Map.of("GHE_TOKEN", " t0ken \n"), "GHE_TOKEN");

        assertThat(source.token()).isEqualTo("t0ken");
        assertThat(source.refresh()).isFalse();
    }

    @Test
    void aFileIsReadAgainWhenItChangesSoARotatedTokenIsUsed(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("token"), "first\n");
        TokenSource source = TokenSource.from(Map.of("GHE_TOKEN_FILE", file.toString()), "GHE_TOKEN");
        assertThat(source.token()).isEqualTo("first");

        Files.writeString(file, "second\n");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(5)));

        assertThat(source.token()).isEqualTo("second");
    }

    @Test
    void refreshingAFileReportsWhetherTheTokenChanged(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("token"), "first");
        TokenSource source = TokenSource.from(Map.of("GHE_TOKEN_FILE", file.toString()), "GHE_TOKEN");
        source.token();

        assertThat(source.refresh()).isFalse();
        Files.writeString(file, "second");
        assertThat(source.refresh()).isTrue();
    }

    @Test
    void bothNoneOrAnEmptyFileAreRefusedNamingTheSettings(@TempDir Path dir) throws IOException {
        Path empty = Files.writeString(dir.resolve("empty"), "\n");

        assertThatThrownBy(() -> TokenSource.from(Map.of("GHE_TOKEN", "a", "GHE_TOKEN_FILE", "/x"), "GHE_TOKEN"))
                .hasMessageContaining("GHE_TOKEN").hasMessageContaining("GHE_TOKEN_FILE");
        assertThatThrownBy(() -> TokenSource.from(Map.of(), "GHE_TOKEN"))
                .hasMessageContaining("GHE_TOKEN or GHE_TOKEN_FILE");
        assertThatThrownBy(() -> TokenSource.from(Map.of("GHE_TOKEN_FILE", empty.toString()), "GHE_TOKEN").token())
                .hasMessageContaining("GHE_TOKEN_FILE");
    }

    @Test
    void theTokenNeverAppearsInItsDescription() {
        assertThat(TokenSource.from(Map.of("GHE_TOKEN", "t0ken"), "GHE_TOKEN").toString())
                .isEqualTo("GHE_TOKEN (variable)").doesNotContain("t0ken");
    }
}
