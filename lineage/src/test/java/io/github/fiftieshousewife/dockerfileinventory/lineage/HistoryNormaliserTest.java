package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    @Test
    void labelGivesItsSortedKeyNamesWithQuotedKeysUnquotedAndQuotedValuesKeptWhole() {
        assertThat(normalise("/bin/sh -c #(nop) LABEL vendor=\"Red Hat, Inc.\"       maintainer=\"Red Hat, Inc.\"").text())
                .isEqualTo("maintainer vendor");
        assertThat(normalise("/bin/sh -c #(nop) LABEL \"org.opencontainers.image.revision\"=\"0e05\" \"build-date\"=\"2026\"")
                .text()).isEqualTo("build-date org.opencontainers.image.revision");
        assertThat(normalise("/bin/sh -c #(nop) LABEL summary=\"say \\\"hi there\\\" b=c\" io.k8s.name=x"))
                .isEqualTo(new NormalStep("LABEL", "io.k8s.name summary", null, false));
    }

    @Test
    void envGivesItsKeyNamesInBothTheKeyValueAndTheLegacyForm() {
        assertThat(normalise("/bin/sh -c #(nop) ENV PYTHON_VERSION=3.11     PATH=$HOME/.local/bin/:$PATH"))
                .isEqualTo(new NormalStep("ENV", "PATH PYTHON_VERSION", null, false));
        assertThat(normalise("/bin/sh -c #(nop) ENV container oci"))
                .isEqualTo(new NormalStep("ENV", "container", null, false));
        assertThat(normalise("/bin/sh -c #(nop) ENV GREETING hello there world").text()).isEqualTo("GREETING");
    }

    @Test
    void otherMetadataGivesItsKeywordAndCollapsedValue() {
        assertThat(List.of(
                "USER 1001", "WORKDIR ${HOME}", "EXPOSE 8080", "ENTRYPOINT [\"container-entrypoint\"]",
                "CMD $STI_SCRIPTS_PATH/usage", "VOLUME /data", "STOPSIGNAL SIGTERM", "SHELL [\"/bin/bash\", \"-c\"]",
                "ONBUILD RUN    make").stream()
                .map(i -> normalise("/bin/sh -c #(nop) " + i))
                .map(n -> n.keyword() + " " + n.text()))
                .containsExactly(
                        "USER 1001", "WORKDIR ${HOME}", "EXPOSE 8080", "ENTRYPOINT [\"container-entrypoint\"]",
                        "CMD $STI_SCRIPTS_PATH/usage", "VOLUME /data", "STOPSIGNAL SIGTERM",
                        "SHELL [\"/bin/bash\", \"-c\"]", "ONBUILD RUN make");
    }

    @Test
    void anythingElseIsOtherWithItsCollapsedText() {
        assertThat(normalise("/bin/sh -c #(nop) FROBNICATE   x"))
                .isEqualTo(new NormalStep("OTHER", "/bin/sh -c #(nop) FROBNICATE x", null, false));
        assertThat(normalise("|1 VERSION=1   /bin/sh -c make").text()).isEqualTo("|1 VERSION=1 /bin/sh -c make");
        assertThat(normalise(null)).isEqualTo(new NormalStep("OTHER", "", null, false));
    }

    @Test
    void knownBuilderAddedStepsAreMarked() {
        assertThat(normalise("/bin/sh -c #(nop) COPY file:9358 in /usr/share/buildinfo/content-sets.json      ")
                .builderAdded()).isTrue();
        assertThat(normalise("/bin/sh -c #(nop) COPY dir:eccb in /root/buildinfo/      ").builderAdded()).isTrue();
        assertThat(normalise("/bin/sh -c #(nop) LABEL \"architecture\"=\"x86_64\" \"vcs-ref\"=\"6\" \"build-date\"=\"2026\"")
                .builderAdded()).isTrue();
        assertThat(normalise("/bin/sh -c #(nop) COPY dir:29d1 in /opt/app/buildinfo-docs").builderAdded()).isFalse();
        assertThat(normalise("/bin/sh -c #(nop) LABEL vcs-ref=6").builderAdded()).isFalse();
    }

    @Test
    void aFromCommentBecomesTheBaseReference() {
        assertThat(HistoryNormaliser.normalise(new HistoryStep("/bin/sh -c #(nop) EXPOSE 8080",
                "FROM registry.access.redhat.com/ubi9/s2i-base:9.8-1779892026", true, null)))
                .isEqualTo(new NormalStep("EXPOSE", "8080", "registry.access.redhat.com/ubi9/s2i-base:9.8-1779892026",
                        false));
        assertThat(HistoryNormaliser.normalise(new HistoryStep("/bin/sh -c make", "built by hand", false, null)).from())
                .isNull();
    }

    private static NormalStep normalise(String createdBy) {
        return HistoryNormaliser.normalise(new HistoryStep(createdBy, null, false, null));
    }
}
