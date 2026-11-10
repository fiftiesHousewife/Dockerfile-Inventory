package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ActionDrilldownTest {

    private static ActionPlanView.PlannedAction action(String check, String severity, String sha, String repo) {
        return new ActionPlanView.PlannedAction(check, severity, "READ", "what " + check, sha, List.of("why " + sha),
                List.of(new ActionPlanView.Where(repo, sha + "/Dockerfile")));
    }

    private static ActionPlanView.PlannedFix fix(String check, String severity, String sha, List<String> copies,
                                                 List<String> inheriting, int locations) {
        return new ActionPlanView.PlannedFix(check, severity, "READ", "what " + check, sha, List.of("why " + sha),
                copies, inheriting, locations);
    }

    static final ActionPlanView VIEW = new ActionPlanView(ActionPlanPageTest.view(1).plan(),
            List.of(action("S5", "DRIFT", "a", "team/api"), action("P6", "BREAKS", "a", "team/api"),
                    action("P6", "BREAKS", "b", "team/web"), action("P6", "BREAKS", "c", "team/web")),
            List.of(fix("S5", "DRIFT", "a", List.of("a"), List.of(), 1),
                    fix("P6", "BREAKS", "a", List.of("a", "b"), List.of("c"), 3),
                    fix("P6", "BREAKS", "d", List.of("d"), List.of(), 1)),
            List.of());

    @Test
    void theActionsAreCountedByCheckWorstFirstThenMostDockerfiles() {
        assertThat(ActionDrilldown.byCheck(VIEW)).containsExactly(
                new ActionDrilldown.CheckRow("P6", "BREAKS", "Shell-form start: the process never receives SIGTERM",
                        3, 2, 4),
                new ActionDrilldown.CheckRow("S5", "DRIFT", "World-writable chmod (777, o+w)", 1, 1, 1));
    }

    @Test
    void eachCheckLinksToItsPageAndEachItsCountsToo() {
        assertThat(ActionDrilldown.checkTable(ActionDrilldown.byCheck(VIEW), "")).contains(
                "<a href=\"checks/P6.html\">P6</a>", "Shell-form start", "<td class=\"num\">3</td>");
    }

    @Test
    void aChecksPageListsItsFixesLargestReachFirstAFixOfSeveralLinkingToItsOwnPage() {
        Map<String, String> pages = ActionDrilldown.pages(VIEW, Set.of("a", "b", "c"));
        String p6 = pages.get("checks/P6.html");

        assertAll(
                () -> assertThat(p6.indexOf("a/Dockerfile")).isLessThan(p6.indexOf("<td>d</td>")),
                () -> assertThat(p6).contains("href=\"../fixes/P6-a.html\"", "what P6"),
                () -> assertThat(pages).containsKey("fixes/P6-a.html").doesNotContainKey("fixes/P6-d.html"),
                () -> assertThat(pages.get("fixes/P6-a.html")).contains("href=\"../dockerfiles/a.html\"",
                        "href=\"../dockerfiles/b.html\"", "href=\"../dockerfiles/c.html\"", "built on them"));
    }

    @Test
    void eachRepositoryHasAPageOfItsActions() {
        Map<String, String> pages = ActionDrilldown.pages(VIEW, Set.of());

        assertAll(
                () -> assertThat(pages).containsKeys("repositories/team-api.html", "repositories/team-web.html"),
                () -> assertThat(pages.get("repositories/team-web.html")).contains("P6", "b/Dockerfile",
                        "c/Dockerfile").doesNotContain("S5"));
    }

    @Test
    void everyCheckHasAPlainName() {
        assertThat(List.of("P1", "P6", "P11", "D7", "S5", "R3", "H9", "I2"))
                .allMatch(check -> !CheckNames.of(check).equals(check));
    }
}
