package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.List;
import java.util.Optional;

final class Dashboard {

    private Dashboard() {
    }

    static String render(Summary summary, Ranking ranking, List<ArchetypeCards.Card> cards,
                         List<BaseRow> bases, Optional<LineageView> lineage) {
        return String.join("\n",
                head(),
                HeadlineSection.render(summary),
                ArchetypeSection.render(summary, ranking, cards),
                BasesSection.render(bases),
                BreakdownSection.render(summary),
                LineageSection.render(lineage),
                "</body></html>", "");
    }

    private static String head() {
        return String.join("\n",
                "<!doctype html>",
                "<html lang=\"en\"><head><meta charset=\"utf-8\">",
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
                "<title>Dockerfile estate</title><style>" + Style.CSS + "</style></head><body>",
                "<h1>Dockerfile estate</h1>",
                "<p class=\"note\">Counts are of Dockerfile locations (each path in each repository)"
                        + " unless stated.</p>");
    }
}
