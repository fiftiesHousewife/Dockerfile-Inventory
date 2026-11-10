package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
