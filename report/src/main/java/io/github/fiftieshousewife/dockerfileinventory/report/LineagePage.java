package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.plural;

import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.ImageRow;
import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.OutdatedRow;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class LineagePage {

    private static final int OPEN_BELOW = 10;
    private static final int DOCKERFILES_SHOWN = 5;

    private record Tree(Map<String, List<ImageRow>> children, Map<String, String> newer, Set<String> gaps,
                        String prefix) {
    }

    private LineagePage() {
    }

    static String body(Optional<LineageView> lineage) {
        return lineage.map(view -> "<p class=\"note\">Every image in Artifactory under the base it was built on. "
                        + "Each says how sure its link to a Dockerfile is:</p><ul class=\"legend-list\">"
                        + LineageSummary.ORDER.stream().map(t -> "<li><strong>" + tier(t) + "</strong>: "
                                + LineageSummary.TIERS.get(t) + "</li>").collect(Collectors.joining())
                        + "</ul>" + tree(view))
                .orElse("<p class=\"note\">Lineage has not run: no images from Artifactory are linked to "
                        + "Dockerfiles. Give LINEAGE_DIR to add it.</p>");
    }

    static String tree(LineageView view) {
        Set<String> names = view.images().stream().map(ImageRow::name).collect(Collectors.toSet());
        String prefix = prefix(names);
        Tree tree = new Tree(view.images().stream().filter(i -> names.contains(i.parent()))
                .collect(Collectors.groupingBy(ImageRow::parent)),
                view.outdated().stream().collect(Collectors.toMap(OutdatedRow::image, OutdatedRow::newer, (a, b) -> a)),
                Set.copyOf(view.gaps()), prefix);
        return (prefix.isEmpty() ? "" : "<p class=\"note\">Every image is in " + escape(prefix.substring(0,
                prefix.length() - 1)) + "; the name leaves it out.</p>")
                + view.images().stream().filter(i -> !names.contains(i.parent())).map(r -> root(r, tree))
                .collect(Collectors.joining());
    }

    static String prefix(Set<String> names) {
        Set<String> firsts = names.stream().map(n -> n.contains("/") ? n.substring(0, n.indexOf('/') + 1) : "")
                .collect(Collectors.toSet());
        return firsts.size() == 1 ? firsts.iterator().next() : "";
    }

    private static String root(ImageRow root, Tree tree) {
        List<ImageRow> below = tree.children().getOrDefault(root.name(), List.of());
        int descendants = descendants(root.name(), tree);
        return "<details" + (below.size() < OPEN_BELOW ? " open" : "") + "><summary>" + line(root, tree)
                + " &#x2013; " + (descendants == 0 ? "nothing in Artifactory is built on it"
                        : plural(descendants, "image", "images") + " built on it") + "</summary>"
                + files(root) + children(below, tree) + "</details>";
    }

    private static int descendants(String name, Tree tree) {
        return tree.children().getOrDefault(name, List.of()).stream()
                .mapToInt(c -> 1 + descendants(c.name(), tree)).sum();
    }

    private static String children(List<ImageRow> below, Tree tree) {
        return below.isEmpty() ? "" : "<ul class=\"tree\">" + below.stream().map(c -> "<li>" + line(c, tree)
                + files(c) + children(tree.children().getOrDefault(c.name(), List.of()), tree) + "</li>")
                .collect(Collectors.joining()) + "</ul>";
    }

    private static String line(ImageRow image, Tree tree) {
        String newer = tree.newer().get(image.name());
        return "<strong>" + escape(image.name().substring(tree.prefix().length())) + "</strong> <span class=\"tier\">"
                + tier(image.tier()) + "</span>"
                + (newer == null ? "" : flag("outdated base: " + escape(newer) + " is newer"))
                + (tree.gaps().contains(image.name()) ? flag("gap: built from no Dockerfile discovery found") : "");
    }

    private static String flag(String text) {
        return " <span class=\"flag\"><span aria-hidden=\"true\">!</span> " + text + "</span>";
    }

    private static String files(ImageRow image) {
        int more = image.dockerfiles().size() - DOCKERFILES_SHOWN;
        return image.dockerfiles().isEmpty() ? "" : "<br><span class=\"note\">" + escape(String.join(", ",
                image.dockerfiles().stream().limit(DOCKERFILES_SHOWN).toList()))
                + (more > 0 ? " and " + count(more) + " more" : "") + "</span>";
    }

    static String tier(String tier) {
        return tier.equals("NONE") ? "no Dockerfile" : LineageSummary.label(tier);
    }
}
