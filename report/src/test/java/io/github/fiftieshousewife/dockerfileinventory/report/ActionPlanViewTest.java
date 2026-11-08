package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ActionPlanViewTest {

    @Test
    void theValidatorsSummaryAndActionsAreReadFromItsFolder() {
        ActionPlanView view = ActionPlanView.read(Path.of("src/test/resources/fixtures/validator"));

        assertAll(
                () -> assertThat(view.plan().profile()).isEqualTo("openshift-4-restricted-v2"),
                () -> assertThat(view.plan().verdicts()).containsEntry("breaks", 1992),
                () -> assertThat(view.actions()).hasSize(2),
                () -> assertThat(view.actions().getFirst().where()).isNotEmpty());
    }
}
