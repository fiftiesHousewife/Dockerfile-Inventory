package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.plural;

import io.github.fiftieshousewife.dockerfileinventory.report.Figures.Apart;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class SummaryPage {

    private SummaryPage() {
    }

    static String estate(Report r) {
        Figures f = r.figures();
        boolean effective = !r.estate().effectiveUsers().isEmpty();
        return "<p class=\"lead\">" + scope(f) + " across " + plural(r.organisations().size(), "organisation",
                "organisations") + ".</p>" + passes(r.passes(), r.archetypes()) + parse(f)
                + apart(r.organisations()) + StripSection.render(f, r.organisations())
                + (r.organisations().size() > 1 ? OrganisationsSection.render(r.organisations()) : "")
                + appliesToEach(r) + SummarySections.archetypes(r.rows(), r.pages(), "", true)
                + SummarySections.bases(f, CountTable.organisationsUsing(r.organisations()), "")
                + SummarySections.managers(f) + SummarySections.users(f, effective)
                + LineageSummary.render(r.lineage(), "") + SummarySections.downloads("");
    }

    static String organisation(Report r, Figures f) {
        ArchetypeRows rows = ArchetypeRows.of(r.ranking(), r.estate().within(f.name()));
        List<String> sentences = AppliesTo.sentences(f, r.figures(), r.organisations());
        return "<p class=\"lead\">" + scope(f) + ". <a href=\"../index.html\">Compare with the whole estate</a>"
                + "</p>" + parse(f) + apart(List.of(f)) + StripSection.render(f, List.of())
                + Html.h2("What applies here", "Applies to") + (sentences.isEmpty()
                        ? "<p class=\"note\">Nothing here stands apart from the estate.</p>" : list(sentences))
                + SummarySections.archetypes(rows, r.pages(), "../", false)
                + SummarySections.bases(f, Map.of(), "../") + SummarySections.managers(f)
                + SummarySections.users(f, !r.estate().effectiveUsers().isEmpty()) + SummarySections.downloads("../");
    }

    static String scope(Figures f) {
        return plural(f.locations(), "Dockerfile location", "Dockerfile locations") + " in "
                + plural(f.repositories(), "repository", "repositories");
    }

    static String passes(List<PassCount> passes, int archetypes) {
        return "<p>Counted four ways, each looser than the last" + Html.info("How alike") + ": "
                + passes.stream().map(p -> count(p.groups()) + " " + PASS_NAMES.getOrDefault(p.pass(), p.pass()))
                .collect(Collectors.joining("; ")) + "; " + plural(archetypes, "archetype", "archetypes") + ".</p>";
    }

    private static final Map<String, String> PASS_NAMES = Map.of("blob", "distinct files",
            "text-normal", "once comments and spacing are ignored", "signature", "kinds of image");

    static String parse(Figures f) {
        int templates = f.templates().files();
        int failed = f.counts().get(Health.PARSE_FAILURES).part();
        int read = f.files() - templates - failed;
        return "<p>" + percent(f.files() - templates == 0 ? 0 : (double) read / (f.files() - templates))
                + " of distinct files parsed: " + count(read) + " read, " + count(failed) + " could not be read, "
                + plural(templates, "template", "templates") + " saved under a Dockerfile's name.</p>";
    }

    static String apart(List<Figures> organisations) {
        return line("Metadata-only bundles (FROM scratch, no RUN, no start command)", organisations,
                Figures::bundles, " They are left out of the user, base and archetype figures.")
                + line("Templates saved under a Dockerfile's name", organisations, Figures::templates,
                " They are not Dockerfiles and are left out of the archetypes.");
    }

    static String line(String what, List<Figures> organisations, Function<Figures, Apart> apart, String after) {
        int locations = organisations.stream().mapToInt(f -> apart.apply(f).locations()).sum();
        int files = organisations.stream().mapToInt(f -> apart.apply(f).files()).sum();
        List<Figures> holding = organisations.stream().filter(f -> apart.apply(f).locations() > 0)
                .sorted((a, b) -> apart.apply(b).locations() - apart.apply(a).locations()).toList();
        return locations == 0 ? "" : "<p>" + what + ": " + plural(locations, "location", "locations") + " in "
                + plural(files, "file", "files") + (organisations.size() == 1 ? "" : ", in " + holding.stream()
                .map(f -> escape(f.name()) + " " + count(apart.apply(f).locations()))
                .collect(Collectors.joining(", "))) + "." + after + "</p>";
    }

    static String appliesToEach(Report r) {
        String each = r.organisations().stream().map(f -> Map.entry(f, AppliesTo.sentences(f, r.figures(),
                        r.organisations())))
                .filter(e -> !e.getValue().isEmpty())
                .map(e -> "<h3><a href=\"" + escape(OrganisationsSection.path(e.getKey().name())) + "\">"
                        + escape(e.getKey().name()) + "</a></h3>" + list(e.getValue()))
                .collect(Collectors.joining());
        return each.isEmpty() ? "" : Html.h2("What applies to each organisation", "Applies to") + each;
    }

    private static String list(List<String> sentences) {
        return "<ul>" + sentences.stream().map(s -> "<li>" + escape(s) + "</li>").collect(Collectors.joining())
                + "</ul>";
    }
}
