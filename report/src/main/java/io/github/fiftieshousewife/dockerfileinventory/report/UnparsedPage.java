package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.List;
import java.util.stream.Collectors;

final class UnparsedPage {

    private UnparsedPage() {
    }

    static String body(List<CatalogueEntry> unparsed) {
        if (unparsed.isEmpty()) {
            return "<p>Every Dockerfile parsed.</p>";
        }
        return "<p>" + Html.count(unparsed.size()) + " Dockerfiles could not be parsed: mostly templates filled in "
                + "before the build, or files named like Dockerfiles that are not.</p>"
                + Html.scrollTable("<th>Dockerfile</th><th>Why</th>", unparsed.stream().map(entry -> "<tr><td>"
                        + Html.dockerfileLink("", entry.blobSha(), Drilldowns.firstPlace(entry)) + "</td><td>"
                        + escape(entry.parseError()) + "</td></tr>").collect(Collectors.joining()));
    }
}
