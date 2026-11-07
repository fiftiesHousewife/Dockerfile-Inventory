package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.info;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;

import io.github.fiftieshousewife.dockerfileinventory.report.Health.Metric;
import io.github.fiftieshousewife.dockerfileinventory.report.Health.State;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class StripSection {

    private static final Map<State, String> ICONS = Map.of(State.GOOD, "&#x2713;", State.WATCH, "!",
            State.ACT, "&#x2715;", State.NO_DATA, "?");
    private static final Map<String, String> NEEDS = Map.of(
            Health.EOL, "Needs an end-of-life list (EOL_LIST).",
            Health.RELEASED, "Needs a release index.");

    private StripSection() {
    }

    static String render(Figures figures, List<Figures> organisations) {
        return "<div class=\"strip\">" + Health.of(figures).stream()
                .map(m -> cell(m, Health.contributor(m.name(), organisations)
                        .map(c -> "Most from " + escape(c.organisation()) + ": " + count(c.count()) + " of "
                                + count(c.whole()) + ".")
                        .orElse("")))
                .collect(Collectors.joining()) + "</div>";
    }

    static String cell(Metric metric, String who) {
        boolean known = metric.state() != State.NO_DATA;
        String below = known ? who : NEEDS.getOrDefault(metric.name(), "");
        return "<div class=\"health " + metric.state().word().replace(' ', '-') + "\"><div class=\"label\">"
                + escape(metric.name()) + info(metric.name()) + "</div><div class=\"value\">"
                + (known ? percent(metric.count().share()) : "&#x2013;") + "</div><div class=\"state\">"
                + "<span class=\"icon\" aria-hidden=\"true\">" + ICONS.get(metric.state()) + "</span> "
                + metric.state().word() + "</div>" + (below.isEmpty() ? "" : "<div class=\"who\">" + below + "</div>")
                + "</div>";
    }
}
