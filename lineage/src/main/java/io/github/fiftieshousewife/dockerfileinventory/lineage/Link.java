package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

/**
 * An image's link to the Dockerfiles that could have built it: one group of identical Dockerfiles (Q17), or for a
 * weak link every group tied for the best partial match. {@code parentMissing} marks a root whose Dockerfile names
 * a base Artifactory holds (Q2).
 */
public record Link(Tier tier, List<String> blobShas, boolean parentMissing) {

    public enum Tier {
        STRONG,
        PROBABLE,
        WEAK,
        NONE
    }

    public Link {
        blobShas = List.copyOf(blobShas);
    }
}
