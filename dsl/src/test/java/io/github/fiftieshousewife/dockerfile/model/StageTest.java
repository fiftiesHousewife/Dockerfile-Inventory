package io.github.fiftieshousewife.dockerfile.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageTest {

    private static final BaseImage UBI = new BaseImage("ubi9/ubi-minimal", "9.4", null);

    @Test
    void aStageBuildsOnExactlyOneOfABaseImageOrAnEarlierStage() {
        assertAll(
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new Stage(null, UBI, "build", List.of())),
                () -> assertThatIllegalArgumentException().isThrownBy(() -> new Stage(null, null, null, List.of())));
    }

    @Test
    void instructionsAreCopied() {
        List<Instruction> instructions = new ArrayList<>(List.of(new User("1001")));

        assertThat(new Stage(null, UBI, null, instructions).instructions()).isUnmodifiable();
    }
}
