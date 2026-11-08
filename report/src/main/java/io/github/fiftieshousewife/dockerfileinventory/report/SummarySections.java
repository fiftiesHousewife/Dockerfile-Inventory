package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.h2;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;

import io.github.fiftieshousewife.dockerfileinventory.report.ArchetypeRows.Row;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

// The sections the summary and each organisation's page share.
final class SummarySections {

    static final int TOP = 10;
    static final List<String> DOWNLOADS = List.of("catalogue/dockerfiles.jsonl", "archetypes.jsonl", "passes.json");

    private SummarySections() {
    }

    static String archetypes(ArchetypeRows rows, Set<Integer> pages, String up, boolean chart) {
        int top = rows.rows().stream().limit(TOP).mapToInt(Row::locations).sum();
        return h2("Archetypes", "Top archetypes") + "<p>" + count(rows.k80()) + " archetypes cover 80% of locations "
                + "and " + count(rows.k95()) + " cover 95%; the largest " + Math.min(TOP, rows.rows().size())
                + " cover " + percent(rows.total() == 0 ? 0 : (double) top / rows.total()) + ".</p>"
                + (chart ? CoverageChart.render(ranking(rows)) : "")
                + ArchetypeTable.render(rows.rows(), 0, TOP, pages, up) + ArchetypeTable.apart(rows.apart())
                + "<p><a href=\"" + up + "archetypes.html\">Every archetype, ranked</a></p>";
    }

    static Ranking ranking(ArchetypeRows rows) {
        return new Ranking(rows.rows().stream().map(r -> new Archetype(r.rank(), r.archetype().key(),
                r.locations(), r.blobShas(), r.archetype().signature(), r.archetype().representative(), List.of()))
                .toList(), rows.total(), rows.k80(), rows.k95());
    }

    static String bases(Figures figures, Map<String, Integer> organisationsUsing, String up) {
        return h2("Base families", "Top bases") + CountTable.render("Base family",
                CountTable.bases(figures.families()), organisationsUsing, TOP)
                + CountTable.leftOut(figures.families())
                + "<p><a href=\"" + up + "bases.html\">Every base, by family</a></p>";
    }

    static String managers(Figures figures) {
        return h2("Package managers", "Package managers")
                + "<p class=\"note\">A location using two managers counts for each.</p>"
                + CountTable.render("Manager", figures.managers(), Map.of(), TOP);
    }

    static String users(Figures figures, boolean effective) {
        int total = figures.users().values().stream().mapToInt(Integer::intValue).sum();
        List<String> kinds = OrganisationsSection.USERS;
        return h2("Final user", "Final user") + "<p class=\"note\">" + (effective
                ? "Effective users, from the built images where a Dockerfile links to one; as written otherwise."
                : "As written in the Dockerfile: lineage has not run, so inherited users are not known.") + "</p>"
                + OrganisationsSection.mix(figures.users()).replace("class=\"mix\"", "class=\"mix wide\"")
                + "<ul class=\"legend-list\">" + IntStream.range(0, kinds.size())
                .mapToObj(i -> "<li><span class=\"key s" + (i + 1) + "\"></span>"
                        + OrganisationsSection.label(kinds.get(i)) + " " + count(figures.users()
                        .getOrDefault(kinds.get(i), 0)) + " (" + percent(total == 0 ? 0
                        : (double) figures.users().getOrDefault(kinds.get(i), 0) / total) + ")</li>")
                .collect(Collectors.joining()) + "</ul>";
    }

    static String downloads(String up) {
        return h2("Downloads", "Downloads") + "<ul>" + DOWNLOADS.stream()
                .map(d -> "<li><a href=\"" + up + d + "\">" + d + "</a></li>").collect(Collectors.joining())
                + "</ul>";
    }
}
