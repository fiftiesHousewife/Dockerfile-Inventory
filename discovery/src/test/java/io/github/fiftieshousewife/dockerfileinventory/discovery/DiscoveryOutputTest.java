package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryOutputTest {

    @TempDir
    Path out;

    @Test
    void theLastOutcomeOfARepositoryDecidesWhetherItIsFinished() {
        List<RepoOutcome> outcomes = List.of(
                outcome("pay/api", RepoOutcome.Status.FAILED), outcome("pay/api", RepoOutcome.Status.SCANNED),
                outcome("pay/worker", RepoOutcome.Status.SCANNED), outcome("pay/worker", RepoOutcome.Status.FAILED));

        assertThat(DiscoveryOutput.finished(outcomes)).containsExactly("pay/api");
    }

    @Test
    void readsBackTheManifestAndOutcomesItRecorded() {
        var output = new DiscoveryOutput(out);
        var located = new Located("pay/api", "c1", Scope.RELEASED, "Dockerfile", "d1");

        output.record(new RepoScan.Result(outcome("pay/api", RepoOutcome.Status.SCANNED), List.of(located)));

        assertThat(output).satisfies(
                o -> assertThat(o.manifest()).containsExactly(located),
                o -> assertThat(o.finishedRepositories()).containsExactly("pay/api"));
    }

    @Test
    void readsBackTheBlobsItReported() {
        var output = new DiscoveryOutput(out);

        output.recordBlobFinding(new DiscoveryOutput.BlobFinding("b1", "pay/api", "binary"));

        assertThat(output.reportedBlobs()).containsExactly("b1");
    }

    @Test
    void storesABlobUnderItsSha() throws IOException {
        var output = new DiscoveryOutput(out);

        output.storeBlob("d1", "FROM ubi9\n");

        assertThat(output.hasBlob("d1")).isTrue();
        assertThat(Files.readString(out.resolve("blobs/d1"))).isEqualTo("FROM ubi9\n");
    }

    @Test
    void readsAMissingFileAsNoRows() {
        assertThat(new DiscoveryOutput(out).read("repos.jsonl", RepoOutcome.class)).isEmpty();
    }

    @Test
    void writesNoFileForNoRows() {
        new DiscoveryOutput(out).append("manifest.jsonl", List.of());

        assertThat(out.resolve("manifest.jsonl")).doesNotExist();
    }

    private static RepoOutcome outcome(String repository, RepoOutcome.Status status) {
        return new RepoOutcome(repository, status, Scope.DEFAULT_BRANCH, "c1", false, false, null, 0, null);
    }
}
