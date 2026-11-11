package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.file;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.repo;
import static io.github.fiftieshousewife.dockerfileinventory.discovery.FakeGitHub.tree;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class PerOrganisationTest {

    private static final String A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private final FakeGitHub pay = new FakeGitHub().repository("pay", repo("pay/api", A))
            .tree("pay/api", A, tree(file("Dockerfile", "one"))).blob("one", "FROM ubi9\n");
    private final FakeGitHub web = new FakeGitHub().blob("two", "FROM alpine\n");
    private final PerOrganisation gitHub = new PerOrganisation(org -> Map.of("pay", pay, "web", web).get(org),
            () -> List.of("pay", "web"));

    @Test
    void eachCallGoesToItsOrganisationsClientWhateverTheCase() {
        assertAll(
                () -> assertThat(gitHub.repositories("Pay")).hasSize(1),
                () -> assertThat(gitHub.tree("pay/api", A, true)).isPresent(),
                () -> assertThat(pay.treeCalls).containsExactly("pay/api"));
    }

    @Test
    void aBatchIsSplitByOrganisationEachPartSentWithItsOwnCredential() {
        List<BlobText> texts = gitHub.blobs(List.of(new BlobRequest("pay/api", "one"),
                new BlobRequest("web/site", "two")));

        assertAll(
                () -> assertThat(texts).extracting(BlobText::sha).containsExactlyInAnyOrder("one", "two"),
                () -> assertThat(pay.blobCalls).singleElement().satisfies(batch -> assertThat(batch).hasSize(1)),
                () -> assertThat(web.blobCalls).singleElement().satisfies(batch -> assertThat(batch).hasSize(1)));
    }

    @Test
    void theHeadCommitIsAskedOfTheOrganisationsClient() {
        pay.heads.put("Pay/api", A);

        assertThat(gitHub.headCommit(repo("Pay/api", null))).isEqualTo(A);
    }

    @Test
    void theCallsLeftAreEachOrganisationsClientsCount() {
        Pending pending = new Pending(List.of(new BlobRequest("pay/api", "one"), new BlobRequest("web/site", "two")),
                List.of(), List.of());

        assertThat(gitHub.calls(pending)).isEqualTo(2);
    }

    @Test
    void theOrganisationsAreThoseTheCredentialsReach() {
        assertThat(gitHub.organizations()).containsExactly("pay", "web");
    }
}
