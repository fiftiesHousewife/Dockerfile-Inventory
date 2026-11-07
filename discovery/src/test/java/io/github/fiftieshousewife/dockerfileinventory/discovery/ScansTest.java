package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.file;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.repo;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScansTest {

    private static final String HEAD = "1111111111111111111111111111111111111111";

    @TempDir
    Path out;

    private final FakeGitHub gitHub = new FakeGitHub()
            .tree("pay/api", HEAD, tree(file("Dockerfile", "d1")))
            .tree("pay/worker", HEAD, tree(file("Dockerfile", "d2")));

    @Test
    void scansEveryRepositoryAndKeepsTheirOrder() {
        List<RepoScan.Result> results = scans().scanAll(List.of(repo("pay/api", HEAD), repo("pay/worker", HEAD)));

        assertThat(results).extracting(r -> r.outcome().repository()).containsExactly("pay/api", "pay/worker");
    }

    @Test
    void recordsEachScanAsItFinishes() throws InterruptedException {
        var output = new DiscoveryOutput(out);

        new Scans(new RepoScan(gitHub, ReleaseIndex.empty()), output, 1).scanOne(repo("pay/api", HEAD));

        assertThat(output.manifest()).extracting(Located::blobSha).containsExactly("d1");
    }

    @Test
    void turnsAGitHubFailureIntoAFailedOutcome() {
        gitHub.failingRepositories.add("pay/api");

        RepoScan.Result result = scans().scanOrFail(repo("pay/api", HEAD));

        assertThat(result.outcome()).satisfies(
                o -> assertThat(o.status()).isEqualTo(RepoOutcome.Status.FAILED),
                o -> assertThat(o.detail()).contains("502"));
    }

    @Test
    void aReasonToStopTheWholeRunComesOutAsItself() {
        SsoRequired sso = new SsoRequired("required; url=https://github.com/orgs/pay/sso");

        assertThatThrownBy(() -> Scans.result(CompletableFuture.failedFuture(sso))).isSameAs(sso);
    }

    @Test
    void reportsAScanTaskThatThrewAsAFailedScan() {
        CompletableFuture<RepoScan.Result> failed = CompletableFuture.failedFuture(new IllegalArgumentException("x"));

        assertThatIllegalStateException().isThrownBy(() -> Scans.result(failed)).withMessage("scan task failed");
    }

    private Scans scans() {
        return new Scans(new RepoScan(gitHub, ReleaseIndex.empty()), new DiscoveryOutput(out), 2);
    }
}
