package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.HashMap;
import java.util.Map;

record LinkRule(double threshold, double margin) {

    // Measured 8 Nov on OKD: correct pairs score 0.8 to 1.0, the closest beating its runner-up by 0.06.
    static final LinkRule DEFAULT = new LinkRule(0.8, 0.05);

    Map<String, Link> credit(Map<String, Link> links) {
        Map<String, Link> credited = new HashMap<>();
        links.forEach((digest, link) -> credited.put(digest, link.withCredited(credits(link))));
        return credited;
    }

    boolean credits(Link link) {
        boolean single = link.blobShas().size() == 1;
        return switch (link.tier()) {
            case STRONG -> true;
            case PROBABLE -> single;
            case WEAK -> single && aligned(link);
            case NONE -> false;
        };
    }

    boolean aligned(Link link) {
        return link.score() >= threshold && (link.runnerUp() == null || link.score() - link.runnerUp() >= margin);
    }
}
