package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

// score and runnerUp: a WEAK link's best alignment and the best of Dockerfiles with other steps (equal for a tie,
// null when no other steps were scored); null for an exact match.
record Link(Tier tier, List<String> blobShas, boolean parentMissing, Double score, Double runnerUp) {

    static final Link NONE = new Link(Tier.NONE, List.of(), false);

    enum Tier {
        STRONG,
        PROBABLE,
        WEAK,
        NONE
    }

    Link {
        blobShas = List.copyOf(blobShas);
    }

    Link(Tier tier, List<String> blobShas, boolean parentMissing) {
        this(tier, blobShas, parentMissing, null, null);
    }
}
