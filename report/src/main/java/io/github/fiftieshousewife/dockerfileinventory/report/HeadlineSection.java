package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.h2;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.info;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.row;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.tile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

final class HeadlineSection {

    private static final Map<String, String> PASS_NAMES = Map.of(
            "blob", "Different files",
            "text-normal", "Still different once comments and spacing are ignored",
            "signature", "Build a different kind of image");

    private HeadlineSection() {
    }

    static String render(Summary summary) {
        return String.join("\n",
                health(Health.of(summary)),
                tiles(summary),
                h2("How many really different Dockerfiles?", "How alike"), passes(summary),
                h2("Rates", "Rates"), rates(summary));
    }

    static String health(List<Health.Metric> metrics) {
        return "<div class=\"tiles strip\">" + metrics.stream().map(HeadlineSection::metric)
                .collect(Collectors.joining()) + "</div>";
    }

    static String metric(Health.Metric metric) {
        String state = metric.state().name().toLowerCase(Locale.ROOT);
        return "<div class=\"health " + state + "\"><div class=\"state\">" + state
                + "</div><div class=\"value\">" + percent(metric.value()) + "</div><div class=\"label\">"
                + escape(metric.name()) + info(metric.name()) + "</div></div>";
    }

    static String tiles(Summary s) {
        List<PassCount> p = s.passes();
        return "<div class=\"tiles\">" + tile("Dockerfile locations", count(s.locations()))
                + tile("Unique files", count(p.get(0).groups()))
                + tile("Unique after normalising text", count(p.get(1).groups()))
                + tile("Unique signatures", count(p.get(2).groups()))
                + tile("Archetypes", count(s.nearDuplicateGroups()))
                + tile("Archetypes covering 80%", count(s.k80()))
                + tile("Archetypes covering 95%", count(s.k95())) + "</div>";
    }

    static String passes(Summary s) {
        String rows = s.passes().stream()
                .map(p -> row(escape(PASS_NAMES.getOrDefault(p.pass(), p.pass())), count(p.groups()),
                        count(p.locations())))
                .collect(Collectors.joining());
        return "<table><tr><th>Counted as</th><th class=\"num\">How many</th><th class=\"num\">Locations</th></tr>"
                + rows + row("Archetypes: families of near-identical images", count(s.nearDuplicateGroups()),
                count(s.locations())) + "</table>";
    }

    static String rates(Summary s) {
        return "<table>" + row("Parse failures (of distinct files, templates left out)", percent(s.parseFailureRate()))
                + row("Templates saved under a Dockerfile's name", count(s.templates()))
                + row("RUN instructions in another shell (PowerShell, cmd), not read", count(s.otherShellRuns()))
                + row("RUN instructions too complex to read", percent(s.opaqueRunRate()))
                + row("Bases pinned by digest", percent(s.pinnedShare()))
                + row("Bases at end of life", percent(s.eolShare()))
                + row("Scanned at a released commit", percent(s.releasedShare()))
                + row("Locations whose file was never parsed", count(s.missing())) + "</table>";
    }
}
