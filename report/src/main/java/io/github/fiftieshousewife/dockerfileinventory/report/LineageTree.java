package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import io.github.fiftieshousewife.dockerfileinventory.report.LineageView.ImageRow;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class LineageTree {

    private static final int DOCKERFILES_SHOWN = 5;

    private LineageTree() {
    }

    static String render(List<ImageRow> images) {
        Set<String> names = images.stream().map(ImageRow::name).collect(Collectors.toSet());
        Map<String, List<ImageRow>> children = images.stream()
                .filter(i -> names.contains(i.parent()))
                .collect(Collectors.groupingBy(ImageRow::parent));
        return "<ul class=\"tree\">" + images.stream().filter(i -> !names.contains(i.parent()))
                .map(i -> node(i, children)).collect(Collectors.joining()) + "</ul>";
    }

    static String node(ImageRow image, Map<String, List<ImageRow>> children) {
        List<ImageRow> below = children.getOrDefault(image.name(), List.of());
        return "<li><code>" + escape(image.name()) + "</code> · " + dockerfiles(image)
                + (below.isEmpty() ? "" : "<ul>" + below.stream().map(c -> node(c, children))
                        .collect(Collectors.joining()) + "</ul>")
                + "</li>";
    }

    static String dockerfiles(ImageRow image) {
        int more = image.dockerfiles().size() - DOCKERFILES_SHOWN;
        return image.dockerfiles().isEmpty() ? "no Dockerfile"
                : image.tier().toLowerCase(Locale.ROOT) + ": " + image.dockerfiles().stream().limit(DOCKERFILES_SHOWN)
                        .map(d -> "<code>" + escape(d) + "</code>").collect(Collectors.joining(", "))
                        + (more > 0 ? " and " + count(more) + " more" : "");
    }
}
