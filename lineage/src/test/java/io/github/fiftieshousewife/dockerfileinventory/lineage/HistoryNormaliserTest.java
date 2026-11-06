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

    @Test
    void copyAndAddGiveTheirDestinationWithVariablesKept() {
        assertThat(normalise("/bin/sh -c #(nop) COPY dir:29d12dec77eb710cd in $STI_SCRIPTS_PATH      "))
                .isEqualTo(new NormalStep("COPY", "$STI_SCRIPTS_PATH", null, false));
        assertThat(normalise("/bin/sh -c #(nop) COPY file:1376702515d596f4 in /etc/yum.repos.d/.      "))
                .isEqualTo(new NormalStep("COPY", "/etc/yum.repos.d/.", null, false));
        assertThat(normalise("/bin/sh -c #(nop) ADD file:base in / "))
                .isEqualTo(new NormalStep("ADD", "/", null, false));
    }

    private static NormalStep normalise(String createdBy) {
        return HistoryNormaliser.normalise(new HistoryStep(createdBy, null, false, null));
    }
}
