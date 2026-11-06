package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

/** Images no Dockerfile accounts for (D1): built here but unmatched, or external bases mirrored in. */
public record GapReport(List<GapRow> gaps, List<GapRow> externalBases) {

    public GapReport {
        gaps = List.copyOf(gaps);
        externalBases = List.copyOf(externalBases);
    }
}
