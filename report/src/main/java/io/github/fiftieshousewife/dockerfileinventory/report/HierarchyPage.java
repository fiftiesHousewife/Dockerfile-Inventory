package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class HierarchyPage {

    private final Map<String, CatalogueEntry> bySha;

    HierarchyPage(List<CatalogueEntry> entries) {
        this.bySha = entries.stream().collect(Collectors.toMap(CatalogueEntry::blobSha, Function.identity(),
                (first, second) -> first));
    }

    static String body(List<CatalogueEntry> entries) {
        return new HierarchyPage(entries).body();
    }

    String body() {
        List<CatalogueEntry> linked = bySha.values().stream().filter(e -> e.links() != null).toList();
        long alone = linked.stream().filter(e -> parents(e).isEmpty() && children(e).isEmpty()).count();
        List<CatalogueEntry> roots = linked.stream().filter(e -> parents(e).isEmpty() && !children(e).isEmpty())
                .sorted(Comparator.comparing(HierarchyPage::label)).toList();
        return "<p>" + roots.size() + (roots.size() == 1 ? " tree; " : " trees; ") + alone
                + (alone == 1 ? " Dockerfile stands alone" : " Dockerfiles stand alone")
                + ", built on nothing in the estate and with nothing built on it.</p>"
                + roots.stream().map(root -> "<ul>" + node(root, new HashSet<>()) + "</ul>")
                .collect(Collectors.joining());
    }

    String node(CatalogueEntry entry, Set<String> path) {
        path.add(entry.blobSha());
        List<CatalogueEntry> below = children(entry).stream().filter(e -> !path.contains(e.blobSha()))
                .sorted(Comparator.comparing(HierarchyPage::label)).toList();
        String li = "<li><a href=\"dockerfiles/" + escape(entry.blobSha()) + ".html\">"
                + escape(entry.locations().getFirst().repository()) + "</a> " + escape(rest(entry))
                + " <span class=\"note\">" + escape(detail(entry)) + "</span>"
                + (below.isEmpty() ? "" : "<ul>" + below.stream().map(child -> node(child, path))
                        .collect(Collectors.joining()) + "</ul>")
                + "</li>";
        path.remove(entry.blobSha());
        return li;
    }

    private List<CatalogueEntry> parents(CatalogueEntry entry) {
        return known(entry.links().parents());
    }

    private List<CatalogueEntry> children(CatalogueEntry entry) {
        return known(entry.links().children());
    }

    List<CatalogueEntry> known(List<CatalogueLinks.Link> links) {
        return links.stream().map(link -> bySha.get(link.blobSha())).filter(e -> e != null).distinct().toList();
    }

    static String detail(CatalogueEntry entry) {
        int images = entry.hierarchy().images().size();
        String changed = entry.locations().stream().map(CatalogueEntry.Place::lastChanged).filter(c -> c != null)
                .max(Comparator.naturalOrder()).map(c -> ", last changed " + c.substring(0, 10)).orElse("");
        List<String> unmaintained = entry.hierarchy().unmaintained();
        return entry.hierarchy().layers().total() + " layers, " + images + (images == 1 ? " image" : " images")
                + changed + (unmaintained.isEmpty() ? "" : ", unmaintained: " + String.join("; ", unmaintained));
    }

    static String rest(CatalogueEntry entry) {
        int more = entry.locations().size() - 1;
        return entry.locations().getFirst().path() + (more > 0 ? " (and " + more + " more)" : "");
    }

    private static String label(CatalogueEntry entry) {
        return entry.locations().getFirst().repository() + "/" + rest(entry);
    }
}
