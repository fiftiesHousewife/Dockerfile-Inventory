package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

// score and runnerUp: a WEAK link's best alignment and the best of Dockerfiles with other steps (equal for a tie,
// null when no other steps were scored); null for an exact match. credited: LinkRule's decision.
record Link(Tier tier, List<String> blobShas, boolean parentMissing, Double score, Double runnerUp,
            boolean credited) {

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

    Link(Tier tier, List<String> blobShas, boolean parentMissing, Double score, Double runnerUp) {
        this(tier, blobShas, parentMissing, score, runnerUp, false);
    }

    Link(Tier tier, List<String> blobShas, boolean parentMissing) {
        this(tier, blobShas, parentMissing, null, null);
    }

    Link withCredited(boolean decision) {
        return new Link(tier, blobShas, parentMissing, score, runnerUp, decision);
    }
}
