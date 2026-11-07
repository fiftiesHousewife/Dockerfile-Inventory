package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.report.StackedChart.Segment;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BreakdownSectionTest {

    @Test
    void managersFollowTheFixedOrderThenAnyOthersAlphabetically() {
        assertThat(BreakdownSection.managers(Map.of("zz", 1, "apk", 2, "dnf", 3, "aa", 4)))
                .extracting(Segment::label).containsExactly("dnf", "apk", "aa", "zz");
    }

    @Test
    void aManagerOutsideTheFixedOrderRanksAfterAllOfIt() {
        assertThat(BreakdownSection.managerRank("dnf")).isZero();
        assertThat(BreakdownSection.managerRank("zypper")).isEqualTo(6);
        assertThat(BreakdownSection.managerRank("pip")).isEqualTo(7);
    }

    @Test
    void usersAreEveryKindInAFixedOrderWithZeroForAKindNotSeen() {
        assertThat(BreakdownSection.users(Map.of("NAMED", 2))).containsExactly(new Segment("root", 0),
                new Segment("numeric", 0), new Segment("named", 2), new Segment("unresolved", 0));
    }

    @Test
    void theParseOutcomeSplitsDistinctFilesIntoParsedTemplatesAndFailed() {
        assertThat(BreakdownSection.parseOutcome(DashboardTest.SUMMARY)).containsExactly(
                new Segment("parsed", 879), new Segment("templates", 3), new Segment("failed", 18));
    }

    @Test
    void theParseOutcomeOfAnEmptyEstateIsAllZero() {
        Summary empty = new Summary(0, 0, List.of(), 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), false, 0, 0, 0);

        assertThat(BreakdownSection.parseOutcome(empty)).extracting(Segment::count).containsOnly(0);
    }

    @Test
    void aCountsTableListsTheLargestFirst() {
        assertThat(BreakdownSection.counts(Map.of("a", 1, "b", 2), "User")).isEqualTo(
                "<table><tr><th>User</th><th class=\"num\">Locations</th></tr>"
                        + "<tr><td>b</td><td class=\"num\">2</td></tr><tr><td>a</td><td class=\"num\">1</td></tr>"
                        + "</table>");
    }

    @Test
    void theUserNoteSaysWhetherUsersAreEffectiveOrAsWritten() {
        assertThat(BreakdownSection.userNote(DashboardTest.SUMMARY)).contains("as written in the Dockerfile");
    }
}
