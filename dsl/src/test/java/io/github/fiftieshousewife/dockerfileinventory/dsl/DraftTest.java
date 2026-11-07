package io.github.fiftieshousewife.dockerfileinventory.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DraftTest {

    private static final BaseImage ALPINE = new BaseImage("alpine", "3.20", null);
    private static final BaseImage UBI = new BaseImage("ubi9/ubi", "9.4", null);

    @Test
    void ofKeepsTheStageSoItRoundTrips() {
        Stage stage = new Stage("app", null, "deps", List.of(new Instruction.Workdir("/app")));

        assertThat(Draft.of(stage).stage()).isEqualTo(stage);
    }

    @Test
    void instructionsIsACopyOfWhatWasAdded() {
        Draft draft = new Draft(null, ALPINE, null);
        List<Instruction> before = draft.instructions();
        draft.add(new Instruction.Workdir("/app"));

        assertThat(before).isEmpty();
        assertThat(draft.instructions()).containsExactly(new Instruction.Workdir("/app"));
    }

    @Test
    void lastUserIsTheLastUserInstructionOrNull() {
        Draft draft = new Draft(null, ALPINE, null);
        assertThat(draft.lastUser()).isNull();

        draft.add(new Instruction.User("0"));
        draft.add(new Instruction.Workdir("/app"));
        draft.add(new Instruction.User("1001"));

        assertThat(draft.lastUser()).isEqualTo("1001");
    }

    @Test
    void baseInFollowsEarlierStagesToTheImageAndALaterNameWins() {
        Draft first = new Draft("deps", ALPINE, null);
        Draft second = new Draft("deps", UBI, null);
        Draft middle = new Draft("mid", null, "deps");
        Draft last = new Draft(null, null, "mid");

        assertThat(last.baseIn(List.of(first, second, middle, last))).isEqualTo(UBI);
        assertThat(first.baseIn(List.of(first))).isEqualTo(ALPINE);
    }
}
