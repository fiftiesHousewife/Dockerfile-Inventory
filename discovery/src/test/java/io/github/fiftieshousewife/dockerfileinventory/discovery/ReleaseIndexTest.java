package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

        assertThat(index).satisfies(
                i -> assertThat(i.size()).isEqualTo(1),
                i -> assertThat(i.releasedCommit("payments/ledger-api")).contains(SHA));
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

    @Test
    void theLastRowForARepositoryWins() throws IOException {
        String later = "f".repeat(40);

        ReleaseIndex index = ReleaseIndex.read(write("pay/api," + SHA + "\npay/api," + later + "\n"));

        assertThat(index.releasedCommit("pay/api")).contains(later);
    }

    @Test
    void skipsBlankLinesCommentsAndAHeaderOnTheFirstLineOnly() {
        assertThat(List.of(
                ReleaseIndex.skipped("", 3),
                ReleaseIndex.skipped("# note", 3),
                ReleaseIndex.skipped("Repository,commit", 0),
                ReleaseIndex.skipped("repo/x," + SHA, 1)))
                .containsExactly(true, true, true, false);
    }

    @Test
    void readsARowAsItsCaseFoldedRepositoryAndCommit() {
        assertThat(ReleaseIndex.row(Path.of("r.csv"), 0, " Pay/API , " + SHA)).isEqualTo(Map.entry("pay/api", SHA));
    }

    @Test
    void refusesARowWithoutARepository() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ReleaseIndex.row(Path.of("r.csv"), 4, "," + SHA))
                .withMessage("r.csv line 5: expected owner/repo,commitSha but was '," + SHA + "'");
    }

    @Test
    void aCommitShaIsFortyHexDigits() {
        assertThat(List.of(ReleaseIndex.isSha(SHA), ReleaseIndex.isSha(SHA.toUpperCase(Locale.ROOT)),
                ReleaseIndex.isSha(SHA.substring(1)), ReleaseIndex.isSha("g" + SHA.substring(1))))
                .containsExactly(true, true, false, false);
    }

    private Path write(String content) throws IOException {
        return Files.writeString(dir.resolve("releases.csv"), content);
    }
}
