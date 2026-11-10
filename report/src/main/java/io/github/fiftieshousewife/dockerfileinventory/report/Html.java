package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Locale;
import java.util.Set;

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

    static String tile(String label, String value, String href) {
        return "<a class=\"tile\" href=\"" + href + "\"><div class=\"value\">" + value + "</div><div class=\"label\">"
                + escape(label) + info(label) + "</div></a>";
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

    static String meter(double fraction) {
        return "<span class=\"meter\" aria-hidden=\"true\"><span style=\"width:" + percent(fraction)
                + "\"></span></span>";
    }

    static String shaLink(String folder, String blobSha) {
        return "<a href=\"" + folder + escape(blobSha) + ".html\"><code>"
                + escape(blobSha.substring(0, Math.min(12, blobSha.length()))) + "</code></a>";
    }

    static String plural(int n, String one, String many) {
        return count(n) + " " + (n == 1 ? one : many);
    }

    static String scrollTable(String headings, String rows) {
        return "<div class=\"scroll\"><table><tr>" + headings + "</tr>" + rows + "</table></div>";
    }

    static String dockerfileLink(String up, String blobSha, String text) {
        return "<a href=\"" + up + "dockerfiles/" + escape(blobSha) + ".html\">" + escape(text) + "</a>";
    }

    static String dockerfileLink(String up, String blobSha, String text, Set<String> pages) {
        return pages.contains(blobSha) ? dockerfileLink(up, blobSha, text) : escape(text);
    }

    static String lowerCase(String text) {
        return escape(text.toLowerCase(Locale.ROOT));
    }
}
