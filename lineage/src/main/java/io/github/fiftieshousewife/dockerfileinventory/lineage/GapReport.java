package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

/** Images no Dockerfile accounts for (D1): built here but unmatched, or external bases mirrored in. */
public record GapReport(List<GapRow> gaps) {

    public GapReport {
        gaps = List.copyOf(gaps);
    }
}
