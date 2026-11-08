package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangeFetchTest {

    @TempDir
    Path out;

    @Test
    void asksInBatchesOfFiftyAndRecordsEveryLocationAnsweredOrNot() {
        FakeGitHub gitHub = new FakeGitHub();
        List<ChangeRequest> requests = IntStream.range(0, 120)
                .mapToObj(i -> new ChangeRequest("pay/mono", "c0", "svc" + i + "/Dockerfile")).toList();
        gitHub.change(requests.get(7), "a1", "2026-03-02T10:11:12Z");
        DiscoveryOutput output = new DiscoveryOutput(out);

        ChangeFetch.Fetched fetched = new ChangeFetch(gitHub, output).fetch(requests);

        assertAll(
                () -> assertThat(gitHub.changeCalls).extracting(List::size).containsExactly(50, 50, 20),
                () -> assertThat(fetched.recorded()).isEqualTo(120),
                () -> assertThat(output.changes()).filteredOn(change -> change.changedAt() != null)
                        .extracting(LastChange::path).containsExactly("svc7/Dockerfile"));
    }

    @Test
    void aBatchGitHubRefusesIsAskedOneLocationAtATimeAndOnlyTheFailingOnesAreLeft() {
        FakeGitHub gitHub = new FakeGitHub();
        gitHub.failingChanges.add("pay/gone");
        ChangeRequest gone = new ChangeRequest("pay/gone", "c0", "Dockerfile");
        ChangeRequest live = new ChangeRequest("pay/live", "c0", "Dockerfile");
        gitHub.change(live, "a1", "2026-03-02T10:11:12Z");
        DiscoveryOutput output = new DiscoveryOutput(out);

        ChangeFetch.Fetched fetched = new ChangeFetch(gitHub, output).fetch(List.of(gone, live));

        assertAll(
                () -> assertThat(fetched).isEqualTo(new ChangeFetch.Fetched(1, 1)),
                () -> assertThat(output.changes()).extracting(LastChange::repository).containsExactly("pay/live"));
    }
}
