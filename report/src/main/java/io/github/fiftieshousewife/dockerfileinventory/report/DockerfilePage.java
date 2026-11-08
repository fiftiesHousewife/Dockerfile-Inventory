package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.List;
import java.util.stream.Collectors;

// One Dockerfile's catalogue entry made readable; it sits in dockerfiles/, beside the other Dockerfiles' pages.
final class DockerfilePage {

    private DockerfilePage() {
    }

    static String body(CatalogueEntry entry) {
        String places = table(List.of("Repository", "Path", "Scope", "Last changed"), entry.locations().stream()
                .map(p -> List.of(p.repository(), p.path(), p.scope(), p.lastChanged() == null ? "" : p.lastChanged()))
                .toList());
        if (entry.parseError() != null) {
            return "<h2>Where it is</h2>" + places + "<h2>Did not parse</h2><p>" + escape(entry.parseError()) + "</p>";
        }
        return "<h2>Where it is</h2>" + places + DockerfileSections.image(entry) + DockerfileSections.stages(entry)
                + DockerfileSections.packages(entry) + links(entry) + DockerfileSections.images(entry)
                + actions(entry);
    }

    static String links(CatalogueEntry entry) {
        return "<h2>Above and below</h2><p>Built on: " + linked(entry.links().parents()) + "</p><p>Built on it: "
                + linked(entry.links().children()) + "</p><p>Footprint: " + escape(entry.footprint().footprint())
                + "</p>";
    }

    static String actions(CatalogueEntry entry) {
        Actions a = entry.actions();
        if (a == null) {
            return "";
        }
        return "<h2>Actions</h2><ul><li>Release scope: " + escape(String.valueOf(a.releaseScope())) + "</li>"
                + "<li>A fix reaches " + a.reach().dockerfiles() + " Dockerfiles in " + a.reach().repositories()
                + " repositories and " + a.reach().images() + " images</li>"
                + "<li>Converts: " + escape(a.conversion().roundTrip()) + "</li>"
                + (a.baseLag() == null ? "" : "<li>Base " + escape(a.baseLag().base()) + ": "
                        + escape(a.baseLag().tag()) + ", newest " + escape(a.baseLag().newestTag()) + "</li>")
                + "<li>Near-duplicates: " + a.nearDuplicates().count() + " "
                + a.nearDuplicates().blobShas().stream().map(DockerfilePage::link).collect(Collectors.joining(" "))
                + "</li></ul>";
    }

    private static String linked(List<CatalogueLinks.Link> links) {
        return links.isEmpty() ? "none" : links.stream().map(l -> link(l.blobSha()) + " (" + escape(l.source()) + ")")
                .collect(Collectors.joining(", "));
    }

    private static String link(String blobSha) {
        return Html.shaLink("", blobSha);
    }

    static String table(List<String> headings, List<List<String>> rows) {
        return "<div class=\"scroll\"><table><tr>" + headings.stream().map(h -> "<th>" + escape(h) + "</th>")
                .collect(Collectors.joining()) + "</tr>" + rows.stream().map(row -> "<tr>" + row.stream()
                        .map(cell -> "<td>" + escape(cell) + "</td>").collect(Collectors.joining()) + "</tr>")
                .collect(Collectors.joining()) + "</table></div>";
    }
}
