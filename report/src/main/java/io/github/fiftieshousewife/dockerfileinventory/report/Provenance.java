package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Collection;
import java.util.List;
import java.util.Set;

// Cosign stores a signature, an attestation and an SBOM as tags beside the image: sha256-<digest>.sig, .att, .sbom.
record Provenance(boolean signed, boolean attested, boolean sbom) {

    static Provenance of(List<String> manifestDigests, Set<String> tags) {
        return new Provenance(has(manifestDigests, tags, ".sig"), has(manifestDigests, tags, ".att"),
                has(manifestDigests, tags, ".sbom"));
    }

    static boolean has(Collection<String> manifestDigests, Set<String> tags, String suffix) {
        return manifestDigests.stream().anyMatch(digest -> tags.contains(digest.replace(':', '-') + suffix));
    }
}
