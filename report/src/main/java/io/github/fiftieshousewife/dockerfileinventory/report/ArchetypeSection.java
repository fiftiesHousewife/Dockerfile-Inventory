package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.h2;

import java.util.List;
import java.util.stream.Collectors;

final class ArchetypeSection {

    private static final int REPOSITORIES_SHOWN = 5;

    private ArchetypeSection() {
    }

    static String render(Summary summary, Ranking ranking, List<ArchetypeCards.Card> cards) {
        return String.join("\n",
                h2("Archetype coverage", "Archetype coverage")
                        + "<p class=\"note\">How few archetypes cover most locations: "
                        + count(summary.k80()) + " cover 80%, " + count(summary.k95()) + " cover 95%.</p>"
                        + CoverageChart.render(ranking),
                h2("Largest archetypes", "Top archetypes"),
                cards.stream().map(ArchetypeSection::card).collect(Collectors.joining()));
    }

    static String card(ArchetypeCards.Card c) {
        return "<div class=\"card\"><div class=\"rank\">" + c.rank() + "</div><p>"
                + escape(c.sentence()) + "</p><p class=\"note\">" + count(c.files()) + " Dockerfiles · "
                + count(c.locations()) + " locations · " + c.repositories().size() + " repositories: "
                + repositories(c.repositories()) + "</p>" + example(c) + "</div>";
    }

    static String repositories(List<String> repositories) {
        return escape(String.join(", ", repositories.stream().limit(REPOSITORIES_SHOWN).toList()))
                + (repositories.size() > REPOSITORIES_SHOWN
                        ? " and " + (repositories.size() - REPOSITORIES_SHOWN) + " more" : "");
    }

    static String example(ArchetypeCards.Card c) {
        return c.exampleText() == null ? "" : "<details><summary>Example: " + escape(c.exampleLocation())
                + "</summary><pre>" + escape(c.exampleText()) + "</pre></details>";
    }
}
