package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.file;
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

        assertThat(result.outcome().status()).isEqualTo(RepoOutcome.Status.SCANNED);
        assertThat(result.found()).containsExactly(
                new Located("pay/api", RELEASED, Scope.RELEASED, "Dockerfile", "d1"),
                new Located("pay/api", RELEASED, Scope.RELEASED, "ops/Containerfile.ubi9", "d2"));
    }

    @Test
    void fallsBackToTheDefaultBranchHeadAndFlagsIt() {
        gitHub.tree("pay/api", HEAD, tree(file("Dockerfile", "d1")));

        RepoScan.Result result = new RepoScan(gitHub, ReleaseIndex.empty()).scan(repo("pay/api", HEAD));

        assertThat(result.outcome().scope()).isEqualTo(Scope.DEFAULT_BRANCH);
        assertThat(result.found()).extracting(Located::commitSha).containsExactly(HEAD);
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

        assertThat(result.outcome().status()).isEqualTo(RepoOutcome.Status.SCANNED);
        assertThat(result.found()).extracting(Located::path).containsExactly("a/Dockerfile", "b/Dockerfile");
    }

    @Test
    void reportsATreeStillTruncatedPastTheDepthLimitButKeepsWhatItListed() {
        gitHub.tree("pay/mono", HEAD, new Tree(true, List.of(file("svc/Dockerfile", "d1"))));

        RepoScan.Result result = new RepoScan(new TreeWalk(gitHub, 0), ReleaseIndex.empty()).scan(repo("pay/mono", HEAD));

        assertThat(result.outcome().status()).isEqualTo(RepoOutcome.Status.TRUNCATED);
        assertThat(result.found()).hasSize(1);
    }

    @Test
    void ignoresADirectoryNamedLikeADockerfile() {
        gitHub.tree("pay/api", HEAD, tree(new Tree.Entry("Dockerfile", "tree", "t1")));

        assertThat(new RepoScan(gitHub, ReleaseIndex.empty()).scan(repo("pay/api", HEAD)).found()).isEmpty();
    }

    private ReleaseIndex releases(String row) throws IOException {
        return ReleaseIndex.read(Files.writeString(dir.resolve("releases.csv"), row));
    }
}
