package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

record GapReport(List<GapRow> gaps, List<GapRow> externalBases, List<Outdated> outdatedBases) {

    GapReport {
        gaps = List.copyOf(gaps);
        externalBases = List.copyOf(externalBases);
        outdatedBases = List.copyOf(outdatedBases);
    }
}
