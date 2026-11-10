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
                + "before the build, or files named like Dockerfiles that are not.</p><div class=\"scroll\"><table>"
                + "<tr><th>Dockerfile</th><th>Why</th></tr>" + unparsed.stream().map(entry -> "<tr><td><a href=\""
                        + "dockerfiles/" + escape(entry.blobSha()) + ".html\">" + escape(entry.locations().getFirst()
                        .repository() + " " + entry.locations().getFirst().path()) + "</a></td><td>"
                        + escape(entry.parseError()) + "</td></tr>").collect(Collectors.joining())
                + "</table></div>";
    }
}
