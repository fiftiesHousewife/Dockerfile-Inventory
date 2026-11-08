package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchetypeTableTest {

    private static final ArchetypeRows ROWS = ArchetypeRows.of(ArchetypeRowsTest.RANKING, ArchetypeRowsTest.ESTATE);

    @Test
    void aRowLinksItsNameToItsFirstCataloguedDockerfilesPage() {
        assertThat(ArchetypeTable.row(ROWS.rows().getFirst(), 3, Set.of("a1"), "")).isEqualTo(
                "<tr><td class=\"num\">1</td><td><a href=\"dockerfiles/a1.html\">ubi-minimal 9 with microdnf (git), "
                        + "numeric user</a></td><td class=\"num\">3</td><td>" + Html.meter(1.0) + "</td>"
                        + "<td class=\"num\">1</td><td><span class=\"chip\">acme 3</span></td>"
                        + "<td>acme/api, 66.7%</td></tr>");
    }

    @Test
    void aRowWithNoCataloguedDockerfileNamesItsFirstLocationInstead() {
        assertThat(ArchetypeTable.row(ROWS.rows().get(2), 3, Set.of(), "../"))
                .contains("<td>debian 12, no packages, root<br><span class=\"note\">beta/x: Dockerfile</span></td>");
    }

    @Test
    void organisationsShowTheFirstThreeAsChipsAndCountTheRest() {
        assertThat(ArchetypeTable.chips(Map.of("a", 5, "b", 4, "c", 3, "d", 2, "e", 1)))
                .isEqualTo("<span class=\"chip\">a 5</span> <span class=\"chip\">b 4</span> "
                        + "<span class=\"chip\">c 3</span> and 2 more");
    }

    @Test
    void theTableShowsItsSliceOfTheRowsWithBarsScaledToTheLargestOfAll() {
        assertThat(ArchetypeTable.render(ROWS.rows(), 1, 3, Set.of(), ""))
                .startsWith("<div class=\"scroll\"><table><tr><th class=\"num\">Rank</th><th>Archetype</th>")
                .contains("scratch, no packages, root").contains("debian 12")
                .doesNotContain("ubi-minimal 9").contains(Html.meter(1.0 / 3));
    }

    @Test
    void whatLeftTheRankingIsOneNote() {
        assertThat(ArchetypeTable.apart(ROWS.apart())).isEqualTo("<p class=\"note\">Not ranked: "
                + "bundle (scratch, metadata only): 4 locations in 1 file; template, not a Dockerfile: 1 location in "
                + "1 file; base set by a variable: 1 location in 1 file.</p>");
        assertThat(ArchetypeTable.apart(Map.of())).isEmpty();
    }
}
