package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One self-contained HTML page (Q2): no external scripts, fonts or styles, so it opens from the volume as it is.
 * Every number comes from the {@link Summary}, {@link Ranking} and base catalogue the tables are written from.
 */
public final class Dashboard {

    private static final int TOP = 20;
    /** Below this many images the whole family tree fits on the page (Q1). */
    private static final int TREE_LIMIT = 50;
    private static final int DOCKERFILES_SHOWN = 5;

    private static final String STYLE = """
            :root { --bg: #ffffff; --fg: #1d1d1f; --muted: #6e6e73; --line: #e5e5ea; --tile: #f5f5f7; }
            @media (prefers-color-scheme: dark) {
              :root { --bg: #1c1c1e; --fg: #f2f2f7; --muted: #a1a1a6; --line: #3a3a3c; --tile: #2c2c2e; }
            }
            body { margin: 0 auto; max-width: 1100px; padding: 24px 16px; background: var(--bg); color: var(--fg);
                   font: 15px/1.45 system-ui, -apple-system, "Segoe UI", sans-serif; }
            h1 { font-size: 24px; margin: 0 0 4px; } h2 { font-size: 18px; margin: 32px 0 8px; }
            p.note { color: var(--muted); margin: 0 0 12px; }
            .tiles { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
            .tile { background: var(--tile); border-radius: 10px; padding: 12px 14px; }
            .tile .value { font-size: 26px; font-weight: 600; font-variant-numeric: tabular-nums; }
            .tile .label { color: var(--muted); font-size: 13px; }
            table { border-collapse: collapse; width: 100%; font-variant-numeric: tabular-nums; }
            th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--line); vertical-align: top; }
            td.num, th.num { text-align: right; } td.code { font-family: ui-monospace, monospace; font-size: 13px;
            overflow-wrap: anywhere; }
            .scroll { overflow-x: auto; }
            ul.tree, ul.tree ul { list-style: none; padding-left: 20px; } ul.tree { padding-left: 0; }
            ul.tree li { margin: 4px 0; } ul.tree code { font-size: 13px; overflow-wrap: anywhere; }
            """;

    private Dashboard() {
    }

    public static String render(Summary summary, Ranking ranking, List<BaseRow> bases,
                                Optional<LineageView> lineage) {
        return String.join("\n",
                "<!doctype html>",
                "<html lang=\"en\"><head><meta charset=\"utf-8\">",
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
                "<title>Dockerfile estate</title><style>" + STYLE + "</style></head><body>",
                "<h1>Dockerfile estate</h1>",
                "<p class=\"note\">Counts are of Dockerfile locations (each path in each repository)"
                        + " unless stated.</p>",
                tiles(summary),
                "<h2>Collapse passes</h2>", passes(summary),
                "<h2>Rates</h2>", rates(summary),
                "<h2>Top " + TOP + " archetypes</h2>", "<div class=\"scroll\">" + archetypes(ranking) + "</div>",
                "<h2>Top " + TOP + " bases</h2>", "<div class=\"scroll\">" + bases(bases) + "</div>",
                "<h2>Package managers</h2>", counts(summary.managers(), "Manager"),
                "<h2>Final user</h2>", "<p class=\"note\">" + userNote(summary) + "</p>",
                counts(summary.users(), "User"),
                "<h2>Lineage</h2>", lineage.map(Dashboard::lineage).orElse(
                        "<p class=\"note\">Lineage has not run: no images from Artifactory are linked to Dockerfiles."
                                + "</p>"),
                "</body></html>", "");
    }

    private static String tiles(Summary s) {
        List<PassCount> p = s.passes();
        return "<div class=\"tiles\">" + tile("Dockerfile locations", count(s.locations()))
                + tile("Unique files", count(p.get(0).groups()))
                + tile("Unique after normalising text", count(p.get(1).groups()))
                + tile("Unique signatures", count(p.get(2).groups()))
                + tile("Archetypes", count(s.nearDuplicateGroups()))
                + tile("Archetypes covering 80%", count(s.k80()))
                + tile("Archetypes covering 95%", count(s.k95())) + "</div>";
    }

    private static String tile(String label, String value) {
        return "<div class=\"tile\"><div class=\"value\">" + value + "</div><div class=\"label\">"
                + escape(label) + "</div></div>";
    }

    private static String passes(Summary s) {
        String rows = s.passes().stream()
                .map(p -> row(escape(p.pass()), count(p.groups()), count(p.locations())))
                .collect(Collectors.joining());
        return "<table><tr><th>Pass</th><th class=\"num\">Groups</th><th class=\"num\">Locations</th></tr>"
                + rows + row("near-duplicate", count(s.nearDuplicateGroups()), count(s.locations())) + "</table>";
    }

    private static String rates(Summary s) {
        return "<table>" + row("Parse failures (of distinct files, templates left out)", percent(s.parseFailureRate()))
                + row("Templates saved under a Dockerfile's name", count(s.templates()))
                + row("RUN instructions too complex to read", percent(s.opaqueRunRate()))
                + row("Bases pinned by digest", percent(s.pinnedShare()))
                + row("Bases at end of life", percent(s.eolShare()))
                + row("Scanned at a released commit", percent(s.releasedShare()))
                + row("Locations whose file was never parsed", count(s.missing())) + "</table>";
    }

    private static String archetypes(Ranking ranking) {
        String rows = ranking.archetypes().stream().limit(TOP)
                .map(a -> "<tr><td class=\"num\">" + a.rank() + "</td><td class=\"num\">" + count(a.locations())
                        + "</td><td class=\"num\">" + count(a.blobShas().size()) + "</td><td class=\"code\">"
                        + escape(a.signature()) + "</td></tr>")
                .collect(Collectors.joining());
        return "<table><tr><th class=\"num\">Rank</th><th class=\"num\">Locations</th>"
                + "<th class=\"num\">Files</th><th>Signature</th></tr>" + rows + "</table>";
    }

    private static String bases(List<BaseRow> bases) {
        String rows = bases.stream().limit(TOP)
                .map(b -> "<tr><td class=\"code\">" + escape(b.path()) + "</td><td class=\"code\">"
                        + escape(b.tag() == null ? "" : b.tag()) + "</td><td class=\"num\">" + count(b.locations())
                        + "</td><td class=\"num\">" + count(b.pinnedLocations()) + "</td><td>" + escape(b.status())
                        + "</td></tr>")
                .collect(Collectors.joining());
        return "<table><tr><th>Image</th><th>Tag</th><th class=\"num\">Locations</th>"
                + "<th class=\"num\">Pinned</th><th>Status</th></tr>" + rows + "</table>";
    }

    private static String counts(Map<String, Integer> counts, String heading) {
        String rows = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> row(escape(e.getKey()), count(e.getValue())))
                .collect(Collectors.joining());
        return "<table><tr><th>" + heading + "</th><th class=\"num\">Locations</th></tr>" + rows + "</table>";
    }

    private static String lineage(LineageView view) {
        LineageView.Counts c = view.counts();
        int linked = c.linksByTier().entrySet().stream()
                .filter(e -> !e.getKey().equals("NONE")).mapToInt(Map.Entry::getValue).sum();
        return "<div class=\"tiles\">" + tile("Images in Artifactory", count(c.images()))
                + tile("With a known parent", percent(c.coverage()))
                + tile("Linked to a Dockerfile", count(linked))
                + tile("No Dockerfile found (gaps)", count(c.gaps()))
                + tile("Built outside (external bases)", count(c.externalBases()))
                + tile("On an outdated base", count(c.outdatedBases())) + "</div>"
                + (view.images().size() < TREE_LIMIT ? tree(view.images()) : lists(view));
    }

    /** Q1: on a large estate, the lists someone acts on, each cut to the top {@link #TOP}. */
    private static String lists(LineageView view) {
        String gaps = view.gaps().stream().limit(TOP).map(g -> "<li><code>" + escape(g) + "</code></li>")
                .collect(Collectors.joining());
        String outdated = view.outdated().stream().limit(TOP)
                .map(o -> "<tr><td class=\"code\">" + escape(o.image()) + "</td><td class=\"code\">"
                        + escape(o.base()) + "</td><td class=\"code\">" + escape(o.newer()) + "</td></tr>")
                .collect(Collectors.joining());
        String bases = view.bases().stream().limit(TOP)
                .map(b -> "<tr><td class=\"code\">" + escape(b.name()) + "</td><td class=\"num\">"
                        + count(b.children()) + "</td></tr>")
                .collect(Collectors.joining());
        return "<h3>" + heading("No Dockerfile found", view.gaps().size(), ", newest first") + "</h3><ul>" + gaps
                + "</ul><h3>" + heading("On an outdated base", view.outdated().size(), "") + "</h3>"
                + "<div class=\"scroll\"><table><tr><th>Image</th><th>Built on</th><th>Newer base</th></tr>"
                + outdated + "</table></div>"
                + "<h3>" + heading("Most-used bases", view.bases().size(), "") + "</h3>"
                + "<div class=\"scroll\"><table><tr><th>Base</th><th class=\"num\">Images built on it</th></tr>"
                + bases + "</table></div>";
    }

    private static String heading(String title, int total, String order) {
        return title + (total <= TOP ? " (" + count(total) + order + ")"
                : " (top " + TOP + " of " + count(total) + order + ")");
    }

    /** Every image under its parent; roots are images with no parent, or more than one candidate. */
    private static String tree(List<LineageView.ImageRow> images) {
        Set<String> names = images.stream().map(LineageView.ImageRow::name).collect(Collectors.toSet());
        Map<String, List<LineageView.ImageRow>> children = images.stream()
                .filter(i -> names.contains(i.parent()))
                .collect(Collectors.groupingBy(LineageView.ImageRow::parent));
        return "<ul class=\"tree\">" + images.stream().filter(i -> !names.contains(i.parent()))
                .map(i -> node(i, children)).collect(Collectors.joining()) + "</ul>";
    }

    private static String node(LineageView.ImageRow image, Map<String, List<LineageView.ImageRow>> children) {
        List<LineageView.ImageRow> below = children.getOrDefault(image.name(), List.of());
        return "<li><code>" + escape(image.name()) + "</code> · " + dockerfiles(image)
                + (below.isEmpty() ? "" : "<ul>" + below.stream().map(c -> node(c, children))
                        .collect(Collectors.joining()) + "</ul>")
                + "</li>";
    }

    private static String dockerfiles(LineageView.ImageRow image) {
        int more = image.dockerfiles().size() - DOCKERFILES_SHOWN;
        return image.dockerfiles().isEmpty() ? "no Dockerfile"
                : image.tier().toLowerCase(Locale.ROOT) + ": " + image.dockerfiles().stream().limit(DOCKERFILES_SHOWN)
                        .map(d -> "<code>" + escape(d) + "</code>").collect(Collectors.joining(", "))
                        + (more > 0 ? " and " + count(more) + " more" : "");
    }

    private static String userNote(Summary s) {
        return s.effectiveUsers()
                ? "Users are effective, from the built images where a Dockerfile links to one; as written otherwise."
                : "Users are as written in the Dockerfile: lineage has not run, so inherited users are not known.";
    }

    private static String row(String... cells) {
        StringBuilder row = new StringBuilder("<tr><td>").append(cells[0]).append("</td>");
        for (int i = 1; i < cells.length; i++) {
            row.append("<td class=\"num\">").append(cells[i]).append("</td>");
        }
        return row.append("</tr>").toString();
    }

    private static String count(int value) {
        return String.format(Locale.UK, "%,d", value);
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.1f%%", share * 100);
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
