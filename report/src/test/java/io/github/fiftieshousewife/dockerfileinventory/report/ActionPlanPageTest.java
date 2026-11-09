package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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
                verdicts, fixes + 4, fixes, Map.of("team/api", 1, "team/web", 3)),
                shas.stream().map(sha -> new ActionPlanView.PlannedAction("P6", "BREAKS", "READ",
                        "write the ENTRYPOINT in exec form", sha, List.of("shell form"),
                        List.of(new ActionPlanView.Where("team/api", sha + "/Dockerfile")))).toList(),
                shas.stream().map(sha -> new ActionPlanView.PlannedFix("P6", "BREAKS", "READ",
                        "write the ENTRYPOINT in exec form", sha, List.of("shell form"),
                        List.of(sha, "copy-1", "copy-2"), List.of("child"), 7)).toList());
    }

    @Test
    void theLeadSaysHowManyImagesBreakDriftOrRunUnderTheProfile() {
        assertThat(ActionPlanPage.lead(view(1))).contains("openshift-4-restricted-v2", "3 of 5 images would break",
                "1 drift", "1 run as they are", "2 not judged");
    }

    @Test
    void theLeadSaysHowManyFixesTheActionsComeTo() {
        assertThat(ActionPlanPage.lead(view(2))).contains("6 actions come to 2 fixes");
    }

    @Test
    void theFixesComeFirstEachNamingItsRepresentativeAndCountingWhatItReaches() {
        String body = ActionPlanPage.body(view(2), Set.of("aaa"));

        assertAll(
                () -> assertThat(body).contains("write the ENTRYPOINT in exec form", "shell form"),
                () -> assertThat(body).contains("href=\"dockerfiles/aaa.html\">team/api aaa/Dockerfile</a>"),
                () -> assertThat(body).doesNotContain("href=\"dockerfiles/zzz1.html\""),
                () -> assertThat(body).contains("<td class=\"num\">3</td><td class=\"num\">1</td>"
                        + "<td class=\"num\">7</td>"));
    }

    @Test
    void onlyTheFirstFixesAreShownAndTheRestCounted() {
        assertThat(ActionPlanPage.body(view(ActionPlanPage.SHOWN + 3), Set.of()))
                .contains("and 3 more in the validator's fixes.jsonl");
    }

    @Test
    void theRepositoriesComeWithTheMostActionsFirst() {
        assertThat(ActionPlanPage.body(view(1), Set.of())).containsSubsequence("team/web", "team/api");
    }
}
