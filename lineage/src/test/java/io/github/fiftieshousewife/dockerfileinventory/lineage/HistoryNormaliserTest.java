package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HistoryNormaliserTest {

    @Test
    void runGivesItsCommandWithTheShellPrefixRemovedAndWhitespaceCollapsed() {
        assertThat(normalise("/bin/sh -c yum install -y git &&     yum clean all\t "))
                .isEqualTo(new NormalStep("RUN", "yum install -y git && yum clean all", null, false));
    }

    @Test
    void theKnownBuilderPrefixIsRemovedFromRun() {
        assertThat(normalise("/bin/sh -c     . /cachi2/cachi2.env &&     python3.11 -m venv ${APP_ROOT}").text())
                .isEqualTo("python3.11 -m venv ${APP_ROOT}");
    }

    private static NormalStep normalise(String createdBy) {
        return HistoryNormaliser.normalise(new HistoryStep(createdBy, null, false, null));
    }
}
