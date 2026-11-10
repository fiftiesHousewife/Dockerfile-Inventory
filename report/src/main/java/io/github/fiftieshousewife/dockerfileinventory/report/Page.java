package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

// One self-contained page: its own styles, relative links, no script.
final class Page {

    private Page() {
    }

    static String render(String title, String up, String body) {
        return String.join("\n",
                "<!doctype html>",
                "<html lang=\"en\"><head><meta charset=\"utf-8\">",
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">",
                "<title>" + escape(title) + "</title><style>" + Style.CSS + "</style></head><body>",
                "<nav aria-label=\"Report pages\"><a href=\"" + up + "index.html\">Summary</a><a href=\"" + up
                        + "hierarchy.html\">Hierarchy</a><a href=\"" + up + "blocked.html\">Blocked</a><a href=\""
                        + up + "archetypes.html\">Archetypes</a></nav>",
                "<main><h1>" + escape(title) + "</h1>",
                Sections.foldable(body),
                "</main></body></html>", "");
    }
}
