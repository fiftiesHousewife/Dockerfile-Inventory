package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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
    void infoIsAFocusableMarkerWithTheAboutTextOrNothingForAnUnknownKey() {
        assertAll(
                () -> assertThat(Html.info("Archetypes")).isEqualTo("<span class=\"info\" tabindex=\"0\" data-tip=\""
                        + About.TEXT.get("Archetypes") + "\" aria-label=\"" + About.TEXT.get("Archetypes")
                        + "\">i</span>"),
                () -> assertThat(Html.info("no such key")).isEmpty());
    }

    @Test
    void aTileCarriesItsLabelAndInfoMarker() {
        assertThat(Html.tile("Archetypes", "3")).startsWith("<div class=\"tile\"><div class=\"value\">3</div>"
                + "<div class=\"label\">Archetypes<span class=\"info\"").endsWith("</div></div>");
    }

    @Test
    void aShaLinkPointsIntoItsFolderAndShowsTheStartOfTheSha() {
        assertThat(Html.shaLink("dockerfiles/", "0123456789abcdef")).isEqualTo(
                "<a href=\"dockerfiles/0123456789abcdef.html\"><code>0123456789ab</code></a>");
    }
}
