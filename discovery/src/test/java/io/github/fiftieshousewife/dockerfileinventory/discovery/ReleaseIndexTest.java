package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReleaseIndexTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    @TempDir
    Path dir;

    @Test
    void readsCommitsSkippingHeaderCommentsAndBlankLines() throws IOException {
        Path csv = write("repository,commit\n# exported 2026-11-05\n\npayments/ledger-api," + SHA + "\n");

        ReleaseIndex index = ReleaseIndex.read(csv);

        assertThat(index.size()).isEqualTo(1);
        assertThat(index.releasedCommit("payments/ledger-api")).contains(SHA);
    }

    @Test
    void matchesRepositoryNamesIgnoringCase() throws IOException {
        ReleaseIndex index = ReleaseIndex.read(write("Payments/Ledger-API," + SHA + "\n"));

        assertThat(index.releasedCommit("payments/ledger-api")).contains(SHA);
    }

    @Test
    void hasNoCommitForAnUnlistedRepository() {
        assertThat(ReleaseIndex.empty().releasedCommit("payments/ledger-api")).isEmpty();
    }

    @Test
    void refusesARowWithoutAFullCommitSha() throws IOException {
        Path csv = write("payments/ledger-api,v1.4.2\n");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReleaseIndex.read(csv))
                .withMessageContaining("line 1");
    }

    private Path write(String content) throws IOException {
        return Files.writeString(dir.resolve("releases.csv"), content);
    }
}
