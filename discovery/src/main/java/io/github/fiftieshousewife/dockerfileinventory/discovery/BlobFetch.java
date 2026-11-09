package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class BlobFetch {

    record Fetched(int stored, int problems) {

        Fetched plus(Fetched other) {
            return new Fetched(stored + other.stored, problems + other.problems);
        }
    }

    static final int BATCH = 50;

    private final GitHub gitHub;
    private final DiscoveryOutput output;
    private final BiConsumer<String, String> store;

    BlobFetch(GitHub gitHub, DiscoveryOutput output) {
        this(gitHub, output, output::storeBlob);
    }

    BlobFetch(GitHub gitHub, DiscoveryOutput output, BiConsumer<String, String> store) {
        this.gitHub = gitHub;
        this.output = output;
        this.store = store;
    }

    Fetched fetch(List<BlobRequest> requests) {
        return batches(requests, BATCH).stream().map(this::fetchBatch).reduce(new Fetched(0, 0), Fetched::plus);
    }

    static <T> List<List<T>> batches(List<T> requests, int size) {
        return IntStream.iterate(0, from -> from < requests.size(), from -> from + size)
                .mapToObj(from -> requests.subList(from, Math.min(from + size, requests.size())))
                .toList();
    }

    Fetched fetchBatch(List<BlobRequest> batch) {
        Map<String, BlobText> texts = gitHub.blobs(batch).stream()
                .collect(Collectors.toMap(BlobText::sha, Function.identity(), (a, b) -> a));
        return batch.stream().map(request -> keep(request, texts.get(request.sha())))
                .reduce(new Fetched(0, 0), Fetched::plus);
    }

    Fetched keep(BlobRequest request, BlobText blob) {
        Optional<String> problem = problem(blob);
        if (problem.isPresent()) {
            output.recordBlobFinding(new DiscoveryOutput.BlobFinding(request.sha(), request.repository(),
                    problem.get()));
            return new Fetched(0, 1);
        }
        store.accept(request.sha(), blob.text());
        return new Fetched(1, 0);
    }

    // GitHub returns no text for a binary blob and cuts a very large one: findings, not Dockerfiles to parse.
    static Optional<String> problem(BlobText blob) {
        if (blob == null) {
            return Optional.of("not found");
        }
        if (blob.binary()) {
            return Optional.of("binary");
        }
        if (blob.truncated() || blob.text() == null) {
            return Optional.of("truncated");
        }
        return Optional.empty();
    }
}
