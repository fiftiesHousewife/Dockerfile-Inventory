package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTest {

    @Test
    void escapeReplacesTheFourCharactersThatBreakHtmlTextOrAttributes() {
        assertThat(Html.escape("a & <b> \"c\"")).isEqualTo("a &amp; &lt;b&gt; &quot;c&quot;");
    }

    @Test
    void countsHaveThousandsSeparatorsAndPercentagesOneDecimal() {
        assertThat(Html.count(1234567)).isEqualTo("1,234,567");
        assertThat(Html.percent(0.1234)).isEqualTo("12.3%");
    }

    @Test
    void aRowsFirstCellIsTextAndTheRestAreNumbers() {
        assertThat(Html.row("a", "1", "2"))
                .isEqualTo("<tr><td>a</td><td class=\"num\">1</td><td class=\"num\">2</td></tr>");
    }

    @Test
    void infoIsAFocusableMarkerWithTheAboutTextOrNothingForAnUnknownKey() {
        assertThat(Html.info("Downloads")).isEqualTo("<span class=\"info\" tabindex=\"0\" data-tip=\""
                + About.TEXT.get("Downloads") + "\" aria-label=\"" + About.TEXT.get("Downloads") + "\">i</span>");
        assertThat(Html.info("no such key")).isEmpty();
    }

    @Test
    void aTileAndAHeadingCarryTheirEscapedLabelAndInfoMarker() {
        assertThat(Html.tile("Archetypes", "3")).startsWith("<div class=\"tile\"><div class=\"value\">3</div>"
                + "<div class=\"label\">Archetypes<span class=\"info\"").endsWith("</div></div>");
        assertThat(Html.h2("A <b>", "Downloads")).startsWith("<h2>A &lt;b&gt;<span class=\"info\"").endsWith("</h2>");
    }

    @Test
    void aFoldedTableSitsInADetailsElement() {
        assertThat(Html.folded("<table></table>"))
                .isEqualTo("<details><summary>Table</summary><table></table></details>");
    }

    @Test
    void svgNumbersHaveOneDecimalWhateverTheLocale() {
        assertThat(Svg.number(12.345)).isEqualTo("12.3");
    }
}
