package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;

final class ChangeFetch {

    private final GitHub gitHub;
    private final DiscoveryOutput output;

    ChangeFetch(GitHub gitHub, DiscoveryOutput output) {
        this.gitHub = gitHub;
        this.output = output;
    }

    int fetch(List<ChangeRequest> requests) {
        return BlobFetch.batches(requests, BlobFetch.BATCH).stream().mapToInt(this::fetchBatch).sum();
    }

    private int fetchBatch(List<ChangeRequest> batch) {
        List<LastChange> changes = gitHub.lastChanges(batch);
        output.recordChanges(changes);
        return changes.size();
    }
}
