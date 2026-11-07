package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.count;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.meter;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class OrganisationsSection {

    static final List<String> USERS = List.of("ROOT", "NUMERIC", "NAMED", "UNRESOLVED");

    private OrganisationsSection() {
    }

    static String render(List<Figures> organisations) {
        int largest = organisations.stream().mapToInt(Figures::locations).max().orElse(1);
        return Html.h2("Organisations compared", "Organisations compared") + "<p class=\"legend\">Final user: "
                + IntStream.range(0, USERS.size()).mapToObj(i -> "<span class=\"key s" + (i + 1) + "\"></span>"
                        + label(USERS.get(i))).collect(Collectors.joining(" ")) + "</p>"
                + "<div class=\"scroll\"><table><tr><th>Organisation</th><th class=\"num\">Locations</th><th></th>"
                + "<th class=\"num\">Repositories</th><th class=\"num\">Files</th><th class=\"num\">Root</th>"
                + "<th class=\"num\">Pinned</th><th class=\"num\">Bundles</th><th>Top base family</th>"
                + "<th>Largest repository</th><th>Final user</th></tr>"
                + organisations.stream().map(f -> row(f, largest)).collect(Collectors.joining())
                + "</table></div>";
    }

    static String row(Figures f, int largest) {
        return "<tr><td><a href=\"" + escape(path(f.name())) + "\">" + escape(f.name()) + "</a></td>"
                + "<td class=\"num\">" + count(f.locations()) + "</td><td>"
                + meter((double) f.locations() / largest) + "</td><td class=\"num\">" + count(f.repositories())
                + "</td><td class=\"num\">" + count(f.files()) + "</td><td class=\"num\">"
                + percent(f.counts().get(Health.ROOT).share()) + "</td><td class=\"num\">"
                + percent(f.counts().get(Health.PINNED).share()) + "</td><td class=\"num\">"
                + count(f.bundles().locations()) + "</td><td>" + escape(topFamily(f)) + "</td><td>"
                + escape(f.largestRepository()) + ", "
                + percent(f.locations() == 0 ? 0 : (double) f.largestRepositoryLocations() / f.locations())
                + "</td><td>" + mix(f.users()) + "</td></tr>";
    }

    static String topFamily(Figures f) {
        return f.families().entrySet().stream().filter(e -> !AppliesTo.NOT_BASES.contains(e.getKey()))
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey).orElse("");
    }

    static String mix(Map<String, Integer> users) {
        int total = users.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            return "";
        }
        List<Integer> shown = IntStream.range(0, USERS.size())
                .filter(i -> users.getOrDefault(USERS.get(i), 0) > 0).boxed().toList();
        return "<span class=\"mix\" role=\"img\" aria-label=\"" + shown.stream()
                .map(i -> label(USERS.get(i)) + " " + percent((double) users.get(USERS.get(i)) / total))
                .collect(Collectors.joining(", ")) + "\">" + shown.stream()
                .map(i -> "<span class=\"s" + (i + 1) + "\" style=\"width:"
                        + percent((double) users.get(USERS.get(i)) / total) + "\"></span>")
                .collect(Collectors.joining()) + "</span>";
    }

    static String label(String user) {
        return user.toLowerCase(Locale.ROOT);
    }

    static String path(String organisation) {
        return "orgs/" + organisation.replaceAll("[^A-Za-z0-9._-]", "_") + ".html";
    }
}
