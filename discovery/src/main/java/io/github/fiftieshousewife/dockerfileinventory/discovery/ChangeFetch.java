package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;

final class ChangeFetch {

    record Fetched(int recorded, int problems) {

        Fetched plus(Fetched other) {
            return new Fetched(recorded + other.recorded, problems + other.problems);
        }
    }

    private final GitHub gitHub;
    private final DiscoveryOutput output;

    ChangeFetch(GitHub gitHub, DiscoveryOutput output) {
        this.gitHub = gitHub;
        this.output = output;
    }

    Fetched fetch(List<ChangeRequest> requests) {
        return BlobFetch.batches(requests, BlobFetch.BATCH).stream().map(this::fetchBatch)
                .reduce(new Fetched(0, 0), Fetched::plus);
    }

    Fetched fetchBatch(List<ChangeRequest> batch) {
        try {
            return recorded(gitHub.lastChanges(batch));
        } catch (GitHubException refused) {
            return batch.size() == 1 ? new Fetched(0, 1) : batch.stream().map(request -> fetchBatch(List.of(request)))
                    .reduce(new Fetched(0, 0), Fetched::plus);
        }
    }

    private Fetched recorded(List<LastChange> changes) {
        output.recordChanges(changes);
        return new Fetched(changes.size(), 0);
    }
}
