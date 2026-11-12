package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.time.Instant;
import java.util.List;

final class ChangeFetch {

    record Fetched(int recorded, int problems) {

        Fetched plus(Fetched other) {
            return new Fetched(recorded + other.recorded, problems + other.problems);
        }
    }

    private final GitHub gitHub;
    private final DiscoveryOutput output;
    private final Progress progress;

    ChangeFetch(GitHub gitHub, DiscoveryOutput output) {
        this(gitHub, output, new Progress(() -> 0L, Instant::now));
    }

    ChangeFetch(GitHub gitHub, DiscoveryOutput output, Progress progress) {
        this.gitHub = gitHub;
        this.output = output;
        this.progress = progress;
    }

    Fetched fetch(List<ChangeRequest> requests) {
        return BlobFetch.batches(requests, BlobFetch.BATCH).stream().map(this::fetchCounted)
                .reduce(new Fetched(0, 0), Fetched::plus);
    }

    private Fetched fetchCounted(List<ChangeRequest> batch) {
        Fetched fetched = fetchBatch(batch);
        progress.done(batch.size(), 0);
        return fetched;
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
