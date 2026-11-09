package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.file;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.link;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.repo;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.tree;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoScanTest {

    private static final String HEAD = "1111111111111111111111111111111111111111";
    private static final String RELEASED = "2222222222222222222222222222222222222222";

    @TempDir
    Path dir;

    private final FakeGitHub gitHub = new FakeGitHub();

    @Test
    void findsDockerfilesAtTheReleasedCommit() throws IOException {
        gitHub.tree("pay/api", RELEASED, tree(file("Dockerfile", "d1"), file("src/Main.java", "j1"),
                file("ops/Containerfile.ubi9", "d2")));

        RepoScan.Result result = new RepoScan(gitHub, releases("pay/api," + RELEASED)).scan(repo("pay/api", HEAD));

        assertThat(result).satisfies(
                r -> assertThat(r.outcome().status()).isEqualTo(RepoOutcome.Status.SCANNED),
                r -> assertThat(r.found()).containsExactly(
                        new Located("pay/api", RELEASED, Scope.RELEASED, "Dockerfile", "d1"),
                        new Located("pay/api", RELEASED, Scope.RELEASED, "ops/Containerfile.ubi9", "d2")));
    }

    @Test
    void findsTheYamlThatCouldBeDeploymentManifestsBesideTheDockerfiles() throws IOException {
        gitHub.tree("pay/api", RELEASED, tree(file("Dockerfile", "d1"), file("deploy/api.yaml", "y1"),
                file(".github/workflows/ci.yaml", "y2"), link("deploy/link.yaml", "y3")));

        RepoScan.Result result = new RepoScan(gitHub, releases("pay/api," + RELEASED)).scan(repo("pay/api", HEAD));

        assertThat(result.yaml()).containsExactly(new Located("pay/api", RELEASED, Scope.RELEASED, "deploy/api.yaml",
                "y1"));
    }

    @Test
    void skipsASymlinkNamedLikeADockerfile() {
        gitHub.tree("pay/api", HEAD, tree(file("Dockerfile", "d1"), link("10/Dockerfile.fedora", "l1")));

        RepoScan.Result result = new RepoScan(gitHub, ReleaseIndex.empty()).scan(repo("pay/api", HEAD));

        assertThat(result.found()).extracting(Located::path).containsExactly("Dockerfile");
    }

    @Test
    void fallsBackToTheDefaultBranchHeadAndFlagsIt() {
        gitHub.tree("pay/api", HEAD, tree(file("Dockerfile", "d1")));

        RepoScan.Result result = new RepoScan(gitHub, ReleaseIndex.empty()).scan(repo("pay/api", HEAD));

        assertThat(result).satisfies(
                r -> assertThat(r.outcome().scope()).isEqualTo(Scope.DEFAULT_BRANCH),
                r -> assertThat(r.found()).extracting(Located::commitSha).containsExactly(HEAD));
    }

    @Test
    void reportsAReleaseCommitGitHubDoesNotHave() throws IOException {
        RepoScan.Result result = new RepoScan(gitHub, releases("pay/api," + RELEASED)).scan(repo("pay/api", HEAD));

        assertThat(result.outcome().status()).isEqualTo(RepoOutcome.Status.RELEASE_COMMIT_MISSING);
    }

    @Test
    void reportsARepositoryWithNoCommitsAsEmpty() {
        RepoScan.Result result = new RepoScan(gitHub, ReleaseIndex.empty()).scan(repo("pay/new", null));

        assertThat(result.outcome().status()).isEqualTo(RepoOutcome.Status.EMPTY);
        assertThat(gitHub.treeCalls).isEmpty();
    }

    @Test
    void findsDockerfilesInEverySubtreeOfATruncatedRepository() {
        gitHub.tree("pay/mono", HEAD, new Tree(true, List.of(file("a/Dockerfile", "d1"))))
                .listing("pay/mono", HEAD, tree(FakeGitHub.dir("a", "t-a"), FakeGitHub.dir("b", "t-b")))
                .tree("pay/mono", "t-a", tree(file("Dockerfile", "d1")))
                .tree("pay/mono", "t-b", tree(file("Dockerfile", "d2")));

        RepoScan.Result result = new RepoScan(gitHub, ReleaseIndex.empty()).scan(repo("pay/mono", HEAD));

        assertThat(result).satisfies(
                r -> assertThat(r.outcome().status()).isEqualTo(RepoOutcome.Status.SCANNED),
                r -> assertThat(r.found()).extracting(Located::path).containsExactly("a/Dockerfile", "b/Dockerfile"));
    }

    @Test
    void reportsATreeStillTruncatedPastTheDepthLimitButKeepsWhatItListed() {
        gitHub.tree("pay/mono", HEAD, new Tree(true, List.of(file("svc/Dockerfile", "d1"))));

        RepoScan.Result result = new RepoScan(new TreeWalk(gitHub, 0), ReleaseIndex.empty())
                .scan(repo("pay/mono", HEAD));

        assertThat(result).satisfies(
                r -> assertThat(r.outcome().status()).isEqualTo(RepoOutcome.Status.TRUNCATED),
                r -> assertThat(r.found()).hasSize(1));
    }

    @Test
    void ignoresADirectoryNamedLikeADockerfile() {
        gitHub.tree("pay/api", HEAD, tree(new Tree.Entry("Dockerfile", "040000", "tree", "t1")));

        assertThat(new RepoScan(gitHub, ReleaseIndex.empty()).scan(repo("pay/api", HEAD)).found()).isEmpty();
    }

    @Test
    void aMissingTreeAtTheReleasedCommitIsAMissingReleaseCommitElseAnEmptyRepository() {
        assertThat(List.of(RepoScan.noTree(repo("pay/api", HEAD), Scope.RELEASED, RELEASED),
                RepoScan.noTree(repo("pay/api", HEAD), Scope.DEFAULT_BRANCH, HEAD)))
                .extracting(r -> r.outcome().status())
                .containsExactly(RepoOutcome.Status.RELEASE_COMMIT_MISSING, RepoOutcome.Status.EMPTY);
    }

    @Test
    void scannedCountsTheDockerfilesFoundAndFlagsATruncatedTree() {
        RepoScan.Result result = RepoScan.scanned(repo("pay/api", HEAD), Scope.DEFAULT_BRANCH, HEAD,
                FakeGitHub.truncated(file("Dockerfile", "d1"), file("README.md", "r1")));

        assertThat(result.outcome()).satisfies(
                o -> assertThat(o.status()).isEqualTo(RepoOutcome.Status.TRUNCATED),
                o -> assertThat(o.dockerfiles()).isEqualTo(1));
    }

    @Test
    void aDockerfileIsABlobThatIsNotASymlinkWithADockerfileName() {
        assertThat(List.of(RepoScan.isDockerfile(file("Dockerfile", "d1")),
                RepoScan.isDockerfile(link("Dockerfile", "l1")),
                RepoScan.isDockerfile(FakeGitHub.dir("Dockerfile", "t1")),
                RepoScan.isDockerfile(file("Makefile", "m1"))))
                .containsExactly(true, false, false, false);
    }

    @Test
    void aFailedScanKeepsTheErrorAsItsDetail() {
        RepoScan.Result result = RepoScan.failed(repo("pay/api", HEAD), new GitHubException("returned 502"));

        assertThat(result.outcome()).satisfies(
                o -> assertThat(o.status()).isEqualTo(RepoOutcome.Status.FAILED),
                o -> assertThat(o.detail()).isEqualTo("returned 502"));
    }

    private ReleaseIndex releases(String row) throws IOException {
        return ReleaseIndex.read(Files.writeString(dir.resolve("releases.csv"), row));
    }
}
