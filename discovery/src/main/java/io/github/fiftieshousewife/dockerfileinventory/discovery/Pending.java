package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

record Pending(List<BlobRequest> blobs, List<BlobRequest> yamls, List<ChangeRequest> changes) {

    Pending {
        blobs = List.copyOf(blobs);
        yamls = List.copyOf(yamls);
        changes = List.copyOf(changes);
    }

    static Pending of(DiscoveryOutput output, List<Located> manifest, Collection<Located> firstOfEach,
                      List<Located> yaml) {
        Set<String> reported = output.reportedBlobs();
        return new Pending(missing(firstOfEach, sha -> output.hasBlob(sha) || reported.contains(sha)),
                missing(Discovery.firstByBlob(yaml).values(), sha -> output.hasYaml(sha) || reported.contains(sha)),
                unchanged(manifest, output.changes().stream()
                        .map(c -> new ChangeRequest(c.repository(), c.commitSha(), c.path()))
                        .collect(Collectors.toSet())));
    }

    int calls() {
        return batches(blobs.size()) + batches(yamls.size()) + batches(changes.size());
    }

    Map<String, Pending> byOrganisation() {
        Map<String, List<BlobRequest>> blobsBy = by(blobs, BlobRequest::repository);
        Map<String, List<BlobRequest>> yamlsBy = by(yamls, BlobRequest::repository);
        Map<String, List<ChangeRequest>> changesBy = by(changes, ChangeRequest::repository);
        return Stream.of(blobsBy, yamlsBy, changesBy).flatMap(m -> m.keySet().stream()).distinct()
                .collect(Collectors.toMap(org -> org, org -> new Pending(blobsBy.getOrDefault(org, List.of()),
                        yamlsBy.getOrDefault(org, List.of()), changesBy.getOrDefault(org, List.of()))));
    }

    private static <R> Map<String, List<R>> by(List<R> requests, Function<R, String> repository) {
        return requests.stream().collect(Collectors.groupingBy(r -> PerOrganisation.owner(repository.apply(r))));
    }

    static int batches(int requests) {
        return (requests + BlobFetch.BATCH - 1) / BlobFetch.BATCH;
    }

    static List<ChangeRequest> unchanged(List<Located> manifest, Set<ChangeRequest> recorded) {
        return manifest.stream().map(l -> new ChangeRequest(l.repository(), l.commitSha(), l.path()))
                .filter(request -> !recorded.contains(request)).distinct().toList();
    }

    static List<BlobRequest> missing(Collection<Located> locations, Predicate<String> known) {
        return locations.stream()
                .filter(l -> !known.test(l.blobSha()))
                .map(l -> new BlobRequest(l.repository(), l.blobSha()))
                .toList();
    }
}
