package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingTest {

    private static final String A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void anEmptyOutputLeavesEveryBlobAndLocationToFetch(@TempDir Path out) {
        var first = new Located("pay/api", A, Scope.RELEASED, "Dockerfile", "same");
        var copy = new Located("pay/worker", B, Scope.RELEASED, "Dockerfile", "same");
        var deployment = new Located("pay/api", A, Scope.RELEASED, "deploy.yaml", "yaml");

        Pending pending = Pending.of(new DiscoveryOutput(out), List.of(first, copy), List.of(first),
                List.of(deployment));

        assertAll(
                () -> assertThat(pending.blobs()).containsExactly(new BlobRequest("pay/api", "same")),
                () -> assertThat(pending.yamls()).containsExactly(new BlobRequest("pay/api", "yaml")),
                () -> assertThat(pending.changes()).hasSize(2),
                () -> assertThat(pending.calls()).isEqualTo(3));
    }

    @Test
    void asksOnlyForBlobsNeitherStoredNorReported() {
        var stored = new Located("pay/api", A, Scope.RELEASED, "Dockerfile", "same");
        var missing = new Located("pay/worker", B, Scope.RELEASED, "debug/Dockerfile", "other");

        assertThat(Pending.missing(List.of(stored, missing), "same"::equals))
                .containsExactly(new BlobRequest("pay/worker", "other"));
    }

    @Test
    void asksForTheLastChangeOfEachLocationNotYetRecorded() {
        var recorded = new Located("pay/api", A, Scope.RELEASED, "Dockerfile", "same");
        var unrecorded = new Located("pay/worker", B, Scope.RELEASED, "Dockerfile", "same");

        assertThat(Pending.unchanged(List.of(recorded, unrecorded), Set.of(new ChangeRequest("pay/api", A,
                "Dockerfile")))).containsExactly(new ChangeRequest("pay/worker", B, "Dockerfile"));
    }

    @Test
    void theCallsLeftAreTheBatchesOfFiftyForTheBlobsTheYamlAndTheLastChanges() {
        assertThat(Pending.batches(0) + Pending.batches(1) + Pending.batches(50) + Pending.batches(51))
                .isEqualTo(4);
    }

    @Test
    void splitsByTheOrganisationOwningEachRequestWhateverItsCase() {
        Pending pending = new Pending(List.of(new BlobRequest("Pay/api", "one"), new BlobRequest("web/site", "two")),
                List.of(new BlobRequest("pay/api", "yaml")), List.of(new ChangeRequest("web/site", A, "Dockerfile")));

        assertThat(pending.byOrganisation()).isEqualTo(Map.of(
                "pay", new Pending(List.of(new BlobRequest("Pay/api", "one")), List.of(new BlobRequest("pay/api",
                        "yaml")), List.of()),
                "web", new Pending(List.of(new BlobRequest("web/site", "two")), List.of(),
                        List.of(new ChangeRequest("web/site", A, "Dockerfile")))));
    }
}
