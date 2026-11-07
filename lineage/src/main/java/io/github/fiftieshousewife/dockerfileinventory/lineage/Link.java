package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

record Link(Tier tier, List<String> blobShas, boolean parentMissing) {

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
}
