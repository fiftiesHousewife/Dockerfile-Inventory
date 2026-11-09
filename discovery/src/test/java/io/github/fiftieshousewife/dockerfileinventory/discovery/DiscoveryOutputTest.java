package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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

        assertAll(
                () -> assertThat(output.manifest()).containsExactly(located),
                () -> assertThat(output.finishedRepositories()).containsExactly("pay/api"));
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

        assertAll(
                () -> assertThat(output.hasBlob("d1")).isTrue(),
                () -> assertThat(Files.readString(out.resolve("blobs/d1"))).isEqualTo("FROM ubi9\n"));
    }

    @Test
    void readsBackTheLastChangesItRecorded() {
        var output = new DiscoveryOutput(out);
        var change = new LastChange("pay/api", "c1", "Dockerfile", "a1", "2026-03-02T10:11:12Z");

        output.recordChanges(List.of(change));

        assertThat(output.changes()).containsExactly(change);
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

    @Test
    void aScansYamlIsListedAndStoredApartFromItsDockerfiles(@TempDir Path out) throws IOException {
        DiscoveryOutput output = new DiscoveryOutput(out);
        Located yaml = new Located("pay/api", "c1", Scope.RELEASED, "deploy/api.yaml", "y1");
        output.record(new RepoScan.Result(new RepoOutcome("pay/api", RepoOutcome.Status.SCANNED, Scope.RELEASED,
                "c1", false, false, null, 0, null), List.of(), List.of(yaml)));
        output.storeYaml("y1", "kind: Pod\n");

        assertAll(
                () -> assertThat(output.yaml()).containsExactly(yaml),
                () -> assertThat(output.hasYaml("y1")).isTrue(),
                () -> assertThat(out.resolve("yaml/y1")).hasContent("kind: Pod\n"),
                () -> assertThat(output.hasBlob("y1")).isFalse());
    }
}
