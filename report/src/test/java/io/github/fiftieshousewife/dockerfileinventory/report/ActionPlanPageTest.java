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

    static ActionPlanView view(int actions) {
        Map<String, Integer> verdicts = new LinkedHashMap<>();
        verdicts.put("breaks", 3);
        verdicts.put("drift", 1);
        verdicts.put("runs", 1);
        return new ActionPlanView(new ActionPlanView.Plan("openshift-4-restricted-v2", "OpenShift 4.11 on.", 5, 2,
                verdicts, actions, Map.of("team/api", 1, "team/web", 3)),
                IntStream.range(0, actions).mapToObj(i -> new ActionPlanView.PlannedAction("P6", "BREAKS", "READ",
                        "write the ENTRYPOINT in exec form", i == 0 ? "aaa" : "zzz" + i, List.of("shell form"),
                        List.of(new ActionPlanView.Where("team/api", "Dockerfile")))).toList());
    }

    @Test
    void theLeadSaysHowManyImagesBreakDriftOrRunUnderTheProfile() {
        assertThat(ActionPlanPage.lead(view(1))).contains("openshift-4-restricted-v2", "3 of 5 images would break",
                "1 drift", "1 run as they are", "2 not judged");
    }

    @Test
    void theActionsComeFirstEachLinkingToItsDockerfileWhenTheCatalogueHasIt() {
        String body = ActionPlanPage.body(view(2), Set.of("aaa"));

        assertAll(
                () -> assertThat(body).contains("write the ENTRYPOINT in exec form", "shell form", "team/api"),
                () -> assertThat(body).contains("href=\"dockerfiles/aaa.html\""),
                () -> assertThat(body).doesNotContain("href=\"dockerfiles/zzz1.html\""));
    }

    @Test
    void onlyTheFirstActionsAreShownAndTheRestCounted() {
        assertThat(ActionPlanPage.body(view(ActionPlanPage.SHOWN + 3), Set.of()))
                .contains("and 3 more in the validator's actions.jsonl");
    }

    @Test
    void theRepositoriesComeWithTheMostActionsFirst() {
        assertThat(ActionPlanPage.body(view(1), Set.of())).containsSubsequence("team/web", "team/api");
    }
}
