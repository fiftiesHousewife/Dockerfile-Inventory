package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class SectionsTest {

    @Test
    void eachSectionFoldsUnderItsHeadingOpenToBeginWith() {
        assertThat(Sections.foldable("<p>lead</p><h2>Footprints</h2><table></table><h2>More</h2><ul></ul>"))
                .isEqualTo("<p>lead</p>"
                        + "<details class=\"section\" open><summary><h2>Footprints</h2></summary><table></table>"
                        + "</details>"
                        + "<details class=\"section\" open><summary><h2>More</h2></summary><ul></ul></details>");
    }

    @Test
    void aPageWithoutSectionsIsLeftAlone() {
        assertAll(
                () -> assertThat(Sections.foldable("<p>just text</p>")).isEqualTo("<p>just text</p>"),
                () -> assertThat(Sections.foldable("")).isEmpty());
    }

    @Test
    void smallerHeadingsStayInsideTheirSection() {
        assertThat(Sections.foldable("<h2>Stages</h2><table></table><h3>Copies</h3><table></table>"))
                .isEqualTo("<details class=\"section\" open><summary><h2>Stages</h2></summary><table></table>"
                        + "<h3>Copies</h3><table></table></details>");
    }
}
