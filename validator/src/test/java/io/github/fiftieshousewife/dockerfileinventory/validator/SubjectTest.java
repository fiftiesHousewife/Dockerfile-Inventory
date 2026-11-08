package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class SubjectTest {

    private static Subject.Stage stage(String name, String fromStage) {
        return new Subject.Stage(name, null, fromStage, List.of(), List.of());
    }

    @Test
    void theFinalStagesAreTheLastAndTheStagesItBuildsOnByNameOrIndex() {
        Subject subject = new Subject("aaa", null, null, List.of(stage("base", null), stage("builder", null),
                stage("runtime", "0"), stage(null, "runtime")));

        assertThat(subject.finalStages()).extracting(Subject.Stage::name).containsExactly(null, "runtime", "base");
    }

    @Test
    void theFixturesAreReadWithTheirStages() {
        assertThat(Fixtures.subject("08bf2be5").stages()).hasSize(2);
    }

    @Test
    void aMetadataOnlyBundleIsNotRunnableButAnImageOnScratchWithAStartIs() {
        assertAll(
                () -> assertThat(Fixtures.subject("02810ad6").runnable()).isFalse(),
                () -> assertThat(Fixtures.subject("080b0412").runnable()).isTrue(),
                () -> assertThat(Fixtures.subject("007a290c").runnable()).isTrue());
    }
}
