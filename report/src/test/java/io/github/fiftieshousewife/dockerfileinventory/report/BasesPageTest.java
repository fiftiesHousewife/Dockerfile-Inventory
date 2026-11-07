package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BasesPageTest {

    private static final List<BaseRow> BASES = List.of(
            new BaseRow("scratch", null, "scratch", 50, 0, "unknown", null),
            new BaseRow("ubi9/ubi-minimal", "9.4", "ubi-minimal", 30, 30, "supported", null),
            new BaseRow("ubi8/ubi-minimal", null, "ubi-minimal", 10, 0, "eol", LocalDate.of(2024, 5, 31)),
            new BaseRow("debian", "12", "debian", 20, 0, "unknown", null),
            new BaseRow("unresolved", null, "unresolved", 4, 0, "unknown", null));

    @Test
    void basesAreGroupedByFamilyLargestFirstEachFamilyFoldedWithItsBases() {
        assertThat(BasesPage.body(BASES)).satisfies(page -> {
            assertThat(page).contains("<details><summary><strong>ubi-minimal</strong>: 40 locations on 2 bases, "
                    + "75.0% pinned by digest</summary>");
            assertThat(page).contains("<tr><td class=\"code\">ubi8/ubi-minimal</td><td class=\"code\"></td>"
                    + "<td class=\"num\">10</td><td class=\"num\">0</td><td>end of life (2024-05-31)</td></tr>");
            assertThat(page.indexOf("<strong>ubi-minimal</strong>"))
                    .isLessThan(page.indexOf("<strong>debian</strong>"));
        });
    }

    @Test
    void scratchAndUnresolvedBasesAreToldApartNotListedAsFamilies() {
        assertThat(BasesPage.body(BASES)).contains("Left out: scratch, the empty base, at 50 locations")
                .doesNotContain("<strong>scratch</strong>").doesNotContain("<strong>unresolved</strong>");
    }

    @Test
    void withoutAnEndOfLifeListTheStatusColumnIsLeftOutAndThePageSaysSo() {
        List<BaseRow> unknown = List.of(new BaseRow("debian", "12", "debian", 20, 0, "unknown", null));

        assertThat(BasesPage.body(unknown)).contains("No base matched an end-of-life list")
                .doesNotContain("<th>Status</th>");
    }

    @Test
    void statusReadsInWords() {
        assertThat(BasesPage.status(BASES.get(1))).isEqualTo("supported");
        assertThat(BasesPage.status(BASES.get(3))).isEqualTo("not on the list");
    }
}
