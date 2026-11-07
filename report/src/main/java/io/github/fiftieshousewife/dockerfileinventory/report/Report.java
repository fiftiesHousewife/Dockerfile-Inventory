package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Everything the pages show, computed once.
record Report(Estate estate, Figures figures, List<Figures> organisations, Ranking ranking, ArchetypeRows rows,
              List<PassCount> passes, int archetypes, List<BaseRow> bases, EolList eol,
              Optional<LineageView> lineage, Map<String, List<Location>> locations, Path discoveryDir) {

    // An archetype at a single location gets no page of its own: the table names that location instead.
    static final int PAGE_FROM = 2;

    Report {
        organisations = List.copyOf(organisations);
        passes = List.copyOf(passes);
        bases = List.copyOf(bases);
        locations = Map.copyOf(locations);
    }

    static Report of(Estate estate, Ranking ranking, List<PassCount> passes, int archetypes, EolList eol,
                     Optional<LineageView> lineage, Path discoveryDir) {
        Figures.Sources sources = Figures.Sources.of(estate, eol);
        List<Figures> organisations = estate.organisations().stream()
                .map(o -> Figures.of(o, estate.within(o), eol, sources)).toList();
        return new Report(estate, Figures.of("estate", estate, eol, sources), organisations, ranking,
                ArchetypeRows.of(ranking, estate), passes, archetypes,
                BaseCatalogue.of(estate.withoutBundles(), eol), eol, lineage, ReportRun.locations(estate),
                discoveryDir);
    }

    Set<Integer> pages() {
        return rows.rows().stream().filter(r -> r.locations() >= PAGE_FROM).map(r -> r.archetype().rank())
                .collect(Collectors.toSet());
    }
}
