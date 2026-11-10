package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ActionPlanPageTest {

    static ActionPlanView view(int fixes) {
        Map<String, Integer> verdicts = new LinkedHashMap<>();
        verdicts.put("breaks", 3);
        verdicts.put("drift", 1);
        verdicts.put("runs", 1);
        List<String> shas = IntStream.range(0, fixes).mapToObj(i -> i == 0 ? "aaa" : "zzz" + i).toList();
        return new ActionPlanView(new ActionPlanView.Plan("openshift-4-restricted-v2", "OpenShift 4.11 on.", 5, 2,
                verdicts, fixes + 4, fixes, Map.of("team/api", 1, "team/web", 3), null, null),
                shas.stream().map(sha -> new ActionPlanView.PlannedAction("P6", "BREAKS", "READ",
                        "write the ENTRYPOINT in exec form", sha, List.of("shell form"),
                        List.of(new ActionPlanView.Where("team/api", sha + "/Dockerfile")))).toList(),
                shas.stream().map(sha -> new ActionPlanView.PlannedFix("P6", "BREAKS", "READ",
                        "write the ENTRYPOINT in exec form", sha, List.of("shell form"),
                        List.of(sha, "copy-1", "copy-2"), List.of("child"), 7)).toList(), List.of());
    }

    @Test
    void theLeadSaysHowManyImagesBreakDriftOrRunUnderTheProfile() {
        assertThat(ActionPlanPage.lead(view(1))).contains("openshift-4-restricted-v2", "3 of 5 images would break",
                "1 drift", "1 run as they are", "2 not judged");
    }

    static final ActionPlanView WORKLOADS = ActionPlanView.read(
            Path.of("src/test/resources/fixtures/validator-workloads"));

    @Test
    void withWorkloadsTheLeadSaysHowManyWouldBeRefusedBreakDriftOrRun() {
        assertThat(ActionPlanPage.workloads(WORKLOADS.plan())).isEqualTo("<p>Of 21 workloads, 15 would be refused "
                + "admission, 2 would break and 4 run as they are.</p>");
    }

    @Test
    void withoutWorkloadsTheLeadSaysNothingOfThem() {
        assertThat(ActionPlanPage.workloads(view(1).plan())).isEmpty();
    }

    @Test
    void theLeadSaysHowManyFixesTheActionsComeTo() {
        assertThat(ActionPlanPage.lead(view(2))).contains("6 actions come to 2 fixes");
    }

    @Test
    void theRepositoriesComeWithTheMostActionsFirst() {
        assertThat(ActionPlanPage.body(view(1), Set.of())).containsSubsequence("team/web", "team/api");
    }

    @Test
    void aCataloguedDockerfileLinksToItsPageByItsFirstPlace() {
        assertThat(ActionPlanPage.dockerfile("aaa", new ActionPlanView.Where("team/api", "Dockerfile"), Set.of("aaa")))
                .isEqualTo("<a href=\"dockerfiles/aaa.html\">team/api Dockerfile</a>");
    }

    @Test
    void aDockerfileWithoutAPlaceOrPageIsNamedByItsSha() {
        assertThat(ActionPlanPage.dockerfile("aaa", null, Set.of())).isEqualTo("aaa");
    }

    @Test
    void eachDockerfileKeepsTheFirstPlaceAnyActionNames() {
        assertThat(ActionPlanPage.firstPlaces(view(2).actions()))
                .containsEntry("aaa", new ActionPlanView.Where("team/api", "aaa/Dockerfile"));
    }

    @Test
    void repositoriesAreListedMostActionsFirst() {
        assertThat(ActionPlanPage.repositories(Map.of("team/api", 1, "team/web", 3), Set.of("team/api")))
                .containsSubsequence("team/web", "team/api")
                .contains("<a href=\"repositories/team-api.html\">team/api</a>", "<td>team/web</td>");
    }

    @Test
    void partsAreJoinedWithAndBeforeTheLast() {
        assertAll(
                () -> assertThat(ActionPlanPage.inWords(List.of("a"))).isEqualTo("a"),
                () -> assertThat(ActionPlanPage.inWords(List.of("a", "b", "c"))).isEqualTo("a, b and c"));
    }

    @Test
    void withWorkloadsThePageShowsThemAfterTheFixes() {
        String body = ActionPlanPage.body(WORKLOADS, Set.of());

        assertAll(
                () -> assertThat(body.indexOf("Workloads, worst first")).isGreaterThan(body.indexOf("<h2>What to fix")),
                () -> assertThat(body).contains("DaemonSet sync (openshift-node)"));
    }

    @Test
    void withoutWorkloadsThePageHasNoWorkloadTable() {
        assertThat(ActionPlanPage.body(view(1), Set.of())).doesNotContain("Workloads, worst first");
    }


    @Test
    void thePageLeadsWithTheChecksEachLinkedToItsFixes() {
        assertThat(ActionPlanPage.body(view(2), Set.of())).contains("<h2>What to fix, by check</h2>",
                "<a href=\"checks/P6.html\">P6</a>", "Shell-form start");
    }
}
