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
    void asksInBatchesOfFiftyAndRecordsEachAnswer() {
        FakeGitHub gitHub = new FakeGitHub();
        List<ChangeRequest> requests = IntStream.range(0, 120)
                .mapToObj(i -> new ChangeRequest("pay/mono", "c0", "svc" + i + "/Dockerfile")).toList();
        gitHub.change(requests.get(7), "a1", "2026-03-02T10:11:12Z");
        DiscoveryOutput output = new DiscoveryOutput(out);

        int recorded = new ChangeFetch(gitHub, output).fetch(requests);

        assertAll(
                () -> assertThat(gitHub.changeCalls).extracting(List::size).containsExactly(50, 50, 20),
                () -> assertThat(recorded).isEqualTo(1),
                () -> assertThat(output.changes()).extracting(LastChange::path).containsExactly("svc7/Dockerfile"));
    }
}
