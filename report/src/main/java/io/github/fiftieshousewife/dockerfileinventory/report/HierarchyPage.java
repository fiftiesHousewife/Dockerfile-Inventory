package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// Every tree of Dockerfiles built on one another, from each root down, each node linking to its Dockerfile's page.
final class HierarchyPage {

    private final Map<String, CatalogueEntry> bySha;

    private HierarchyPage(List<CatalogueEntry> entries) {
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

    private List<CatalogueEntry> known(List<CatalogueLinks.Link> links) {
        return links.stream().map(link -> bySha.get(link.blobSha())).filter(e -> e != null).distinct().toList();
    }

    static String rest(CatalogueEntry entry) {
        int more = entry.locations().size() - 1;
        return entry.locations().getFirst().path() + (more > 0 ? " (and " + more + " more)" : "");
    }

    private static String label(CatalogueEntry entry) {
        return entry.locations().getFirst().repository() + "/" + rest(entry);
    }
}
