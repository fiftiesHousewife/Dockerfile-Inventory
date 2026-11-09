package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class RemovedLaterTest {

    @Test
    void h8ReportsAnRmInALaterLayerOfWhatTheFinalImageCopiedIn() {
        assertThat(new RemovedLater().check(Subjects.of("""
                FROM ubi9
                COPY tool.tar.gz setup.sh /tmp/
                COPY src /opt/src
                RUN tar xzf /tmp/tool.tar.gz -C /opt && rm -f /tmp/tool.tar.gz
                RUN make -C /opt/src && rm -rf /opt/src/build
                RUN rm -rf /opt
                ADD rootfs.tar.gz /
                RUN rm /rootfs.tar.gz
                """))).extracting(Finding::line, Finding::message).containsExactly(
                        tuple(4, "rm /tmp/tool.tar.gz removes tool.tar.gz, which COPY on line 2 wrote; its layer "
                                + "still holds it"),
                        tuple(6, "rm /opt removes src, which COPY on line 3 wrote; its layer still holds it"));
    }

    @Test
    void h8IgnoresAFileThatDidNotParse() {
        assertThat(new RemovedLater().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @Test
    void eachSourceLandsAtTheDestinationOrInsideItWhenItIsADirectoryOrThereAreSeveral() {
        assertAll(
                () -> assertThat(RemovedLater.landed(new AddSources(List.of(), List.of("a.sh"), "/usr/bin/run")))
                        .containsExactly("/usr/bin/run"),
                () -> assertThat(RemovedLater.landed(new AddSources(List.of(), List.of("conf/", "b.sh"), "/etc/app")))
                        .containsExactly("/etc/app/conf", "/etc/app/b.sh"),
                () -> assertThat(RemovedLater.landed(new AddSources(List.of(), List.of("a.sh"), "app/")))
                        .isEmpty(),
                () -> assertThat(RemovedLater.landed(new AddSources(List.of(), List.of("."), "/build/")))
                        .containsExactly("/build"));
    }

    @Test
    void anRmRemovesItsAbsolutePathsNamedOutrightAndNotThoseWithAVariableOrAGlob() {
        assertAll(
                () -> assertThat(RemovedLater.removed(List.of("sudo", "rm", "-rf", "/tmp/a/", "/opt/$X", "/tmp/*.gz")))
                        .containsExactly("/tmp/a"),
                () -> assertThat(RemovedLater.removed(List.of("rmdir", "/tmp/a"))).isEmpty());
    }

    @Test
    void eachSourceOfACopyIsCopiedWithItsKeywordAndLine() {
        assertThat(RemovedLater.copied("COPY a.sh b.sh /opt/", 3)).containsExactly(
                new RemovedLater.Copied("/opt/a.sh", "a.sh", "COPY", 3),
                new RemovedLater.Copied("/opt/b.sh", "b.sh", "COPY", 3));
    }

    @Test
    void anAddOfAnArchiveCopiesNothingItCouldRemove() {
        assertThat(RemovedLater.copied("ADD rootfs.tar.gz /", 1)).isEmpty();
    }

    @Test
    void anRmOfACopiedPathOrARecursiveRmAboveItIsARemoval() {
        List<RemovedLater.Copied> copied = List.of(new RemovedLater.Copied("/opt/src", "src", "COPY", 2));
        assertAll(
                () -> assertThat(RemovedLater.removals(List.of("rm", "-rf", "/opt"), copied)).hasSize(1),
                () -> assertThat(RemovedLater.removals(List.of("rm", "/opt"), copied)).isEmpty(),
                () -> assertThat(RemovedLater.removals(List.of("rm", "/opt/src"), copied)).hasSize(1));
    }

    @Test
    void aRemovalOfTheBuildContextNamesThePathItLanded() {
        assertThat(RemovedLater.removal("/app", new RemovedLater.Copied("/app", ".", "COPY", 2)))
                .isEqualTo("rm /app removes /app, which COPY on line 2 wrote; its layer still holds it");
    }

    @Test
    void anRmIsRecursiveWithAnyShortFlagHoldingRorTheLongFlag() {
        assertAll(
                () -> assertThat(RemovedLater.recursive(List.of("rm", "-fR", "/a"))).isTrue(),
                () -> assertThat(RemovedLater.recursive(List.of("rm", "--recursive", "/a"))).isTrue(),
                () -> assertThat(RemovedLater.recursive(List.of("rm", "--force", "/a"))).isFalse());
    }

    @Test
    void aSourcesNameIsItsLastPathSegment() {
        assertAll(
                () -> assertThat(RemovedLater.name("conf/app/")).isEqualTo("app"),
                () -> assertThat(RemovedLater.name("a.sh")).isEqualTo("a.sh"));
    }
}
