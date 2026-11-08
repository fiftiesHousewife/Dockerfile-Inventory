package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.file;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.repo;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryTest {

    private static final String A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @TempDir
    Path out;

    private final FakeGitHub gitHub = new FakeGitHub()
            .repository("pay", repo("pay/api", A))
            .repository("pay", repo("pay/worker", B))
            .tree("pay/api", A, tree(file("Dockerfile", "same")))
            .tree("pay/worker", B, tree(file("Dockerfile", "same"), file("debug/Dockerfile", "other")))
            .blob("same", "FROM ubi9-minimal\n")
            .blob("other", "FROM ubi9\n");

    @Test
    void fetchesACopyPastedDockerfileOnce() throws IOException {
        Discovery.Summary summary = discovery().run();

        assertAll(
                () -> assertThat(summary.dockerfiles()).isEqualTo(3),
                () -> assertThat(summary.uniqueBlobs()).isEqualTo(2),
                () -> assertThat(gitHub.blobCalls).singleElement().satisfies(batch -> assertThat(batch).hasSize(2)),
                () -> assertThat(Files.readString(out.resolve("blobs/same"))).isEqualTo("FROM ubi9-minimal\n"));
    }

    @Test
    void aSecondRunScansNothingAndFetchesNothing() {
        discovery().run();
        gitHub.treeCalls.clear();
        gitHub.blobCalls.clear();

        Discovery.Summary summary = discovery().run();

        assertAll(
                () -> assertThat(summary.skipped()).isEqualTo(2),
                () -> assertThat(gitHub.treeCalls).isEmpty(),
                () -> assertThat(gitHub.blobCalls).isEmpty());
    }

    @Test
    void retriesAFailedRepositoryOnTheNextRun() {
        gitHub.failingRepositories.add("pay/worker");
        Discovery.Summary first = discovery().run();
        gitHub.failingRepositories.clear();
        gitHub.treeCalls.clear();

        discovery().run();

        assertAll(
                () -> assertThat(first.failed()).isEqualTo(1),
                () -> assertThat(gitHub.treeCalls).containsExactly("pay/worker"));
    }

    @Test
    void recordsBlobsGitHubCannotReturnAsFindings() throws IOException {
        gitHub.blobs.remove("other");
        gitHub.blobs.put("same", new BlobText("same", null, false, true));

        Discovery.Summary summary = discovery().run();

        assertAll(
                () -> assertThat(summary.blobProblems()).isEqualTo(2),
                () -> assertThat(Files.readAllLines(out.resolve("blob-findings.jsonl")))
                        .anySatisfy(line -> assertThat(line).contains("\"binary\""))
                        .anySatisfy(line -> assertThat(line).contains("\"not found\"")));
    }

    @Test
    void fetchesBlobsInBatchesOfFifty() {
        var many = new FakeGitHub();
        var entries = IntStream.range(0, 120).mapToObj(i -> file("svc" + i + "/Dockerfile", "sha" + i))
                .toArray(Tree.Entry[]::new);
        many.repository("pay", repo("pay/mono", A)).tree("pay/mono", A, tree(entries));
        IntStream.range(0, 120).forEach(i -> many.blob("sha" + i, "FROM scratch\n"));

        new Discovery(many, ReleaseIndex.empty(), new DiscoveryOutput(out), 4).run();

        assertThat(many.blobCalls).extracting(List::size).containsExactly(50, 50, 20);
    }

    @Test
    void namedOrganisationsAreTheOnlyOnesScanned() {
        gitHub.repository("ops", repo("ops/tools", A)).tree("ops/tools", A, tree(file("Dockerfile", "same")));

        Discovery.Summary summary = new Discovery(gitHub, ReleaseIndex.empty(), new DiscoveryOutput(out), 4,
                List.of("ops")).run();

        assertAll(
                () -> assertThat(summary.repositories()).isEqualTo(1),
                () -> assertThat(gitHub.treeCalls).containsOnly("ops/tools"));
    }

    @Test
    void scansTheNamedOrganisationsOrElseEveryOne() {
        gitHub.repository("ops", repo("ops/tools", A));
        var output = new DiscoveryOutput(out);

        assertAll(
                () -> assertThat(new Discovery(gitHub, ReleaseIndex.empty(), output, 1, List.of("ops")).repositories())
                        .extracting(Repository::nameWithOwner).containsExactly("ops/tools"),
                () -> assertThat(new Discovery(gitHub, ReleaseIndex.empty(), output, 1).repositories()).hasSize(3));
    }

    @Test
    void leavesOutFinishedRepositories() {
        assertThat(Discovery.unfinished(List.of(repo("pay/api", A), repo("pay/worker", B)), Set.of("pay/api")))
                .extracting(Repository::nameWithOwner).containsExactly("pay/worker");
    }

    @Test
    void keepsTheFirstLocationOfEachBlob() {
        var first = new Located("pay/api", A, Scope.RELEASED, "Dockerfile", "same");
        var copy = new Located("pay/worker", B, Scope.RELEASED, "Dockerfile", "same");

        assertThat(Discovery.firstByBlob(List.of(first, copy))).isEqualTo(Map.of("same", first));
    }

    @Test
    void asksOnlyForBlobsNeitherStoredNorReported() {
        var stored = new Located("pay/api", A, Scope.RELEASED, "Dockerfile", "same");
        var missing = new Located("pay/worker", B, Scope.RELEASED, "debug/Dockerfile", "other");

        assertThat(Discovery.missing(List.of(stored, missing), "same"::equals))
                .containsExactly(new BlobRequest("pay/worker", "other"));
    }

    @Test
    void recordsEachLocationsLastChange() {
        gitHub.change(new ChangeRequest("pay/api", A, "Dockerfile"), "c1", "2026-03-02T10:11:12Z");

        Discovery.Summary summary = discovery().run();

        assertAll(
                () -> assertThat(summary.lastChanges()).isEqualTo(3),
                () -> assertThat(new DiscoveryOutput(out).changes()).extracting(LastChange::changedAt)
                        .containsExactlyInAnyOrder("2026-03-02T10:11:12Z", null, null));
    }

    @Test
    void aSecondRunAsksOnlyForLocationsGitHubRefusedLastTime() {
        gitHub.failingChanges.add("pay/worker");
        discovery().run();
        gitHub.failingChanges.clear();
        gitHub.changeCalls.clear();

        discovery().run();

        assertThat(gitHub.changeCalls).singleElement().satisfies(batch -> assertThat(batch).containsExactly(
                new ChangeRequest("pay/worker", B, "Dockerfile"), new ChangeRequest("pay/worker", B,
                        "debug/Dockerfile")));
    }

    @Test
    void asksForTheLastChangeOfEachLocationNotYetRecorded() {
        var recorded = new Located("pay/api", A, Scope.RELEASED, "Dockerfile", "same");
        var unrecorded = new Located("pay/worker", B, Scope.RELEASED, "Dockerfile", "same");

        assertThat(Discovery.unchanged(List.of(recorded, unrecorded), Set.of(new ChangeRequest("pay/api", A,
                "Dockerfile")))).containsExactly(new ChangeRequest("pay/worker", B, "Dockerfile"));
    }

    private Discovery discovery() {
        return new Discovery(gitHub, ReleaseIndex.empty(), new DiscoveryOutput(out), 4);
    }
}
