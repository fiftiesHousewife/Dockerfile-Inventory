package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChangedAfterCopyTest {

    @Test
    void h6ReportsAChownOrChmodInALaterLayerOnFilesTheFinalImageCopiedIn() {
        assertThat(new ChangedAfterCopy().check(Subjects.of("""
                FROM golang AS build
                COPY . /src
                RUN chown -R 1001:0 /src
                FROM ubi9
                COPY --from=build /src/bin /opt/app/bin
                COPY conf/ /opt/app/conf/
                RUN chown -R 1001:0 /opt/app && chmod 755 /usr/local/bin/tool
                COPY run.sh /usr/local/bin/run.sh
                RUN chmod +x /usr/local/bin/run.sh
                """))).extracting(Finding::line, Finding::message).containsExactly(
                        tuple(7, "chown -R 1001:0 /opt/app rewrites what COPY wrote to /opt/app/bin on line 5"),
                        tuple(9, "chmod +x /usr/local/bin/run.sh rewrites what COPY wrote to /usr/local/bin/run.sh on "
                                + "line 8"));
    }

    @Test
    void h6IgnoresAFileThatDidNotParse() {
        assertThat(new ChangedAfterCopy().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @Test
    void aChangeRewritesACopyWhenItIsTheCopiedPathOrRecursivelyCoversIt() {
        assertAll(
                () -> assertThat(ChangedAfterCopy.rewrites("/opt/app", true, "/opt/app/conf/")).isTrue(),
                () -> assertThat(ChangedAfterCopy.rewrites("/opt/app/conf", false, "/opt/app/conf/")).isTrue(),
                () -> assertThat(ChangedAfterCopy.rewrites("/opt/app", false, "/opt/app/conf/")).isFalse(),
                () -> assertThat(ChangedAfterCopy.rewrites("/opt/application", true, "/opt/app")).isFalse(),
                () -> assertThat(ChangedAfterCopy.rewrites("/opt/app/conf/a.yaml", false, "/opt/app/conf/"))
                        .isTrue());
    }

    @Test
    void aCopyToAKnownAbsolutePathIsCopiedAndOneWithAVariableIsNot() {
        assertAll(
                () -> assertThat(ChangedAfterCopy.copied("add a /opt/a", 4))
                        .contains(new ChangedAfterCopy.Copied("/opt/a", "ADD", 4)),
                () -> assertThat(ChangedAfterCopy.copied("COPY a $HOME/a", 4)).isEmpty(),
                () -> assertThat(ChangedAfterCopy.copied("COPY a app/", 4)).isEmpty());
    }

    @Test
    void aRunsChangeToACopiedPathIsReportedWithTheCopysLine() {
        assertThat(ChangedAfterCopy.messages("RUN chown -R 1001 /opt",
                List.of(new ChangedAfterCopy.Copied("/opt/app", "COPY", 2))))
                .containsExactly("chown -R 1001 /opt rewrites what COPY wrote to /opt/app on line 2");
    }
}
