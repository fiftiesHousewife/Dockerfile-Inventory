package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.file;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.repo;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    void aTryStopsScanningOnceEnoughRepositoriesHeldADockerfile() {
        var org = new FakeGitHub();
        IntStream.range(0, 6).forEach(i -> org.repository("pay", repo("pay/r" + i, A))
                .tree("pay/r" + i, A, i % 2 == 0 ? tree(file("Dockerfile", "same")) : tree()));
        org.blob("same", "FROM ubi9\n");

        Discovery.Summary summary = new Discovery(org, ReleaseIndex.empty(), new DiscoveryOutput(out), 1, List.of(),
                2).run();

        assertAll(
                () -> assertThat(org.treeCalls).containsExactly("pay/r0", "pay/r1", "pay/r2"),
                () -> assertThat(summary.scanned()).isEqualTo(3));
    }

    @Test
    void theYamlBesideTheDockerfilesIsFetchedIntoItsOwnFolderOnce() {
        gitHub.tree("pay/api", A, tree(file("Dockerfile", "same"), file("deploy/api.yaml", "y1"),
                file("deploy/copy.yaml", "y1")));
        gitHub.blob("y1", "kind: Pod\n");

        Discovery.Summary summary = discovery().run();

        assertAll(
                () -> assertThat(out.resolve("yaml/y1")).hasContent("kind: Pod\n"),
                () -> assertThat(out.resolve("blobs/y1")).doesNotExist(),
                () -> assertThat(summary.yamlLocations()).isEqualTo(2),
                () -> assertThat(summary.yamlFetched()).isEqualTo(1));
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

    private Discovery discovery(Selection selection, int maxDockerfiles, Confirmation confirmation) {
        return new Discovery(gitHub, ReleaseIndex.empty(), new DiscoveryOutput(out), 4, List.of(), 0, selection,
                maxDockerfiles, confirmation);
    }

    @Test
    void afterTheFirstPassTheRunIsSummedUpAndAskedToGoOn() {
        List<FirstPass> asked = new ArrayList<>();

        discovery(Selection.ALL, 0, pass -> asked.add(pass)).run();

        assertThat(asked).containsExactly(new FirstPass(2, 2, 3, 2, 2, out.toString()));
    }

    @Test
    void declinedTheRunStopsBeforeFetchingAnythingAndKeepsItsFirstPass() {
        assertAll(
                () -> assertThatThrownBy(() -> discovery(Selection.ALL, 0, pass -> false).run())
                        .isInstanceOf(RunStopped.class).hasMessageContaining("first pass is kept"),
                () -> assertThat(gitHub.blobCalls).isEmpty(),
                () -> assertThat(new DiscoveryOutput(out).manifest()).hasSize(3));
    }

    @Test
    void moreDistinctDockerfilesThanTheCeilingStopsTheRunBeforeAsking() {
        List<FirstPass> asked = new ArrayList<>();

        assertAll(
                () -> assertThatThrownBy(() -> discovery(Selection.ALL, 1, pass -> asked.add(pass)).run())
                        .isInstanceOf(RunStopped.class)
                        .hasMessage("found 2 distinct Dockerfiles, more than MAX_DOCKERFILES 1: narrow the selection "
                                + "or raise it"),
                () -> assertThat(asked).isEmpty(),
                () -> assertThat(gitHub.blobCalls).isEmpty());
    }

    @Test
    void aSelectionScansOnlyTheRepositoriesItKeeps() {
        discovery(new Selection(List.of("pay/worker"), List.of()), 0, Confirmation.ALWAYS).run();

        assertThat(gitHub.treeCalls).allMatch(call -> call.startsWith("pay/worker"));
    }

    @Test
    void aSelectionTheListingDoesNotHoldStopsTheRunBeforeAnyTreeIsWalked() {
        assertAll(
                () -> assertThatThrownBy(() -> discovery(new Selection(List.of("pay/gone"), List.of()), 0,
                        Confirmation.ALWAYS).run()).isInstanceOf(SelectionNotFound.class),
                () -> assertThat(gitHub.treeCalls).isEmpty());
    }

    @Test
    void theCallsLeftAreTheBatchesOfFiftyForTheBlobsTheYamlAndTheLastChanges() {
        assertThat(Discovery.batches(0) + Discovery.batches(1) + Discovery.batches(50) + Discovery.batches(51))
                .isEqualTo(4);
    }
}
