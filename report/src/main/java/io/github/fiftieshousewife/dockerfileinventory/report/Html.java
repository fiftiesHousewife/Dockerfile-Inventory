package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

final class Html {

    private Html() {
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String count(int value) {
        return String.format(Locale.UK, "%,d", value);
    }

    static String percent(double share) {
        return String.format(Locale.ROOT, "%.1f%%", share * 100);
    }

    static String row(String... cells) {
        return "<tr><td>" + cells[0] + "</td>" + Arrays.stream(cells, 1, cells.length)
                .map(cell -> "<td class=\"num\">" + cell + "</td>")
                .collect(Collectors.joining()) + "</tr>";
    }

    static String tile(String label, String value) {
        return "<div class=\"tile\"><div class=\"value\">" + value + "</div><div class=\"label\">"
                + escape(label) + info(label) + "</div></div>";
    }

    static String info(String key) {
        String text = About.TEXT.get(key);
        return text == null ? "" : "<span class=\"info\" tabindex=\"0\" data-tip=\"" + escape(text)
                + "\" aria-label=\"" + escape(text) + "\">i</span>";
    }

    static String h2(String title, String key) {
        return "<h2>" + escape(title) + info(key) + "</h2>";
    }

    static String folded(String table) {
        return "<details><summary>Table</summary>" + table + "</details>";
    }
}
