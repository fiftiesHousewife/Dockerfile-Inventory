package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ActionsTest {

    private static final Map<String, List<Location>> LOCATIONS = Map.of(
            "aaa", List.of(new Location("team/api", "Dockerfile"), new Location("team/web", "Dockerfile")),
            "bbb", List.of(new Location("team/api", "tools/Dockerfile")));

    private static Finding finding(Check check, Severity severity, Certainty certainty, String sha, String message) {
        return new Finding(check, severity, certainty, sha, message);
    }

    @Test
    void aDockerfilesFindingsUnderOneCheckBecomeOneActionSayingWhatToChangeAndWhere() {
        List<Action> actions = Actions.of(List.of(
                finding(Check.P2, Severity.BREAKS, Certainty.INFERRED, "aaa", "VOLUME /data ..."),
                finding(Check.P2, Severity.BREAKS, Certainty.INFERRED, "aaa", "HOME /home/app ...")), LOCATIONS);

        assertThat(actions).containsExactly(new Action(Check.P2, Severity.BREAKS, Certainty.INFERRED,
                Actions.WHAT.get(Check.P2), "aaa", List.of("VOLUME /data ...", "HOME /home/app ..."),
                LOCATIONS.get("aaa")));
    }

    @Test
    void actionsComeBySeverityThenReachThenCertainty() {
        List<Action> actions = Actions.of(List.of(
                finding(Check.P8, Severity.DRIFT, Certainty.READ, "aaa", "unpinned"),
                finding(Check.P2, Severity.BREAKS, Certainty.INFERRED, "bbb", "writes"),
                finding(Check.P6, Severity.BREAKS, Certainty.READ, "bbb", "shell"),
                finding(Check.P3, Severity.BREAKS, Certainty.READ, "aaa", "port")), LOCATIONS);

        assertThat(actions).extracting(a -> a.check() + " " + a.blobSha())
                .containsExactly("P3 aaa", "P6 bbb", "P2 bbb", "P8 aaa");
    }

    @Test
    void everyCheckSaysWhatToChange() {
        assertThat(Actions.WHAT).containsOnlyKeys(Check.values());
    }

    @Test
    void theRepositoryViewListsEachRepositorysActionsInTheSameOrder() {
        List<Action> actions = Actions.of(List.of(
                finding(Check.P3, Severity.BREAKS, Certainty.READ, "aaa", "port"),
                finding(Check.P6, Severity.BREAKS, Certainty.READ, "bbb", "shell")), LOCATIONS);

        Map<String, List<Action>> byRepository = Actions.byRepository(actions);
        assertAll(
                () -> assertThat(byRepository).containsOnlyKeys("team/api", "team/web"),
                () -> assertThat(byRepository.get("team/api")).extracting(Action::blobSha)
                        .containsExactly("aaa", "bbb"),
                () -> assertThat(byRepository.get("team/web")).extracting(Action::blobSha).containsExactly("aaa"));
    }
}
