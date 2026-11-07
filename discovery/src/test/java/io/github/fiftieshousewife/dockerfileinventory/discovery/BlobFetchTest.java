package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlobFetchTest {

    @TempDir
    Path out;

    private final FakeGitHub gitHub = new FakeGitHub().blob("d1", "FROM ubi9\n");

    @Test
    void cutsRequestsIntoBatchesOfTheGivenSize() {
        List<BlobRequest> requests = IntStream.range(0, 7).mapToObj(i -> new BlobRequest("pay/api", "s" + i)).toList();

        assertThat(BlobFetch.batches(requests, 3)).extracting(List::size).containsExactly(3, 3, 1);
    }

    @Test
    void makesNoBatchForNoRequests() {
        assertThat(BlobFetch.batches(List.of(), 50)).isEmpty();
    }

    @Test
    void storesAFoundBlobAndCountsAMissingOneAsAProblem() throws IOException {
        var fetch = new BlobFetch(gitHub, new DiscoveryOutput(out));

        BlobFetch.Fetched fetched = fetch.fetchBatch(List.of(new BlobRequest("pay/api", "d1"),
                new BlobRequest("pay/api", "gone")));

        assertThat(fetched).isEqualTo(new BlobFetch.Fetched(1, 1));
        assertThat(Files.readString(out.resolve("blobs/d1"))).isEqualTo("FROM ubi9\n");
    }

    @Test
    void recordsAProblemBlobAsAFinding() {
        var output = new DiscoveryOutput(out);

        BlobFetch.Fetched kept = new BlobFetch(gitHub, output)
                .keep(new BlobRequest("pay/api", "b1"), new BlobText("b1", null, false, true));

        assertThat(kept).isEqualTo(new BlobFetch.Fetched(0, 1));
        assertThat(output.reportedBlobs()).containsExactly("b1");
    }

    @Test
    void namesWhyABlobCannotBeParsed() {
        assertThat(List.of(
                BlobFetch.problem(null),
                BlobFetch.problem(new BlobText("s", null, false, true)),
                BlobFetch.problem(new BlobText("s", "FROM", true, false)),
                BlobFetch.problem(new BlobText("s", null, false, false)),
                BlobFetch.problem(new BlobText("s", "FROM", false, false))))
                .extracting(problem -> problem.orElse("none"))
                .containsExactly("not found", "binary", "truncated", "truncated", "none");
    }

    @Test
    void addsCounts() {
        assertThat(new BlobFetch.Fetched(1, 2).plus(new BlobFetch.Fetched(3, 4)))
                .isEqualTo(new BlobFetch.Fetched(4, 6));
    }
}
