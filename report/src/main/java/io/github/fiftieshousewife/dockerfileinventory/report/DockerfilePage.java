package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class DockerfilePage {

    private DockerfilePage() {
    }

    static String body(CatalogueEntry entry) {
        return body(entry, Map.of());
    }

    static String body(CatalogueEntry entry, Map<String, String> programs) {
        String places = table(List.of("Repository", "Path", "Scope", "Last changed"), entry.locations().stream()
                .map(place -> List.of(place.repository(), place.path(), place.scope(),
                        place.lastChanged() == null ? "" : place.lastChanged()))
                .toList());
        if (entry.parseError() != null) {
            return "<h2>Where it is</h2>" + places + "<h2>Did not parse</h2><p>" + escape(entry.parseError()) + "</p>";
        }
        return "<h2>Where it is</h2>" + places + DockerfileSections.image(entry) + DockerfileSections.stages(entry)
                + DockerfileSections.packages(entry) + links(entry) + DockerfileSections.images(entry)
                + actions(entry, programs);
    }

    static String links(CatalogueEntry entry) {
        return "<h2>Above and below</h2><p>Built on: " + linked(entry.links().parents()) + "</p><p>Built on it: "
                + linked(entry.links().children()) + "</p><p>Footprint: " + escape(entry.footprint().footprint())
                + "</p>";
    }

    static String actions(CatalogueEntry entry, Map<String, String> programs) {
        Actions actions = entry.actions();
        if (actions == null) {
            return "";
        }
        return "<h2>Actions</h2><ul><li>Release scope: " + escape(String.valueOf(actions.releaseScope())) + "</li>"
                + "<li>A fix reaches " + actions.reach().dockerfiles() + " Dockerfiles in "
                + actions.reach().repositories() + " repositories and " + actions.reach().images() + " images</li>"
                + "<li>Converts: " + escape(actions.conversion().roundTrip()) + "</li>" + baseLag(actions.baseLag())
                + recipe(Programs.split(programs.get(entry.blobSha()), actions.nearDuplicates().blobShas(), programs))
                + "</ul>";
    }

    static String recipe(Programs.Split split) {
        return "<li>Same recipe, same program: " + split.same().size() + " " + split.same().stream()
                .map(DockerfilePage::link).collect(Collectors.joining(" ")) + "</li><li>Same recipe, another program: "
                + split.other().size() + " " + split.other().stream().map(DockerfilePage::link)
                .collect(Collectors.joining(" ")) + "</li>";
    }

    static String baseLag(BaseLags.BaseLag lag) {
        return lag == null ? "" : "<li>Base " + escape(lag.base()) + ": " + escape(lag.tag()) + ", newest "
                + escape(lag.newestTag()) + "</li>";
    }

    static String linked(List<CatalogueLinks.Link> links) {
        return links.isEmpty() ? "none" : links.stream()
                .map(link -> link(link.blobSha()) + " (" + escape(link.source()) + ")")
                .collect(Collectors.joining(", "));
    }

    private static String link(String blobSha) {
        return Html.shaLink("", blobSha);
    }

    static String table(List<String> headings, List<List<String>> rows) {
        return "<div class=\"scroll\"><table><tr>" + headings.stream().map(name -> "<th>" + escape(name) + "</th>")
                .collect(Collectors.joining()) + "</tr>" + rows.stream().map(row -> "<tr>" + row.stream()
                        .map(cell -> "<td>" + escape(cell) + "</td>").collect(Collectors.joining()) + "</tr>")
                .collect(Collectors.joining()) + "</table></div>";
    }
}
