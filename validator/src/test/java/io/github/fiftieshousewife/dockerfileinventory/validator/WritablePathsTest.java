package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WritablePathsTest {

    private final WritablePaths check = new WritablePaths();

    @Test
    void volumesAndAWorkingDirectoryNeverMadeGroupWritableAreProblems() {
        assertThat(check.problems(subject("12baa7e1"), V2)).containsExactly(
                "VOLUME /etc/mts is never made writable by group 0 (chmod g=u or g+w), so OpenShift's arbitrary UID "
                        + "may not write it",
                "VOLUME /etc/fedmsg.d is never made writable by group 0 (chmod g=u or g+w), so OpenShift's "
                        + "arbitrary UID may not write it",
                "WORKDIR /src is never made writable by group 0 (chmod g=u or g+w), so OpenShift's arbitrary UID may "
                        + "not write it");
    }

    @Test
    void sclorgsFixPermissionsScriptMakesItsPathGroupWritableBelowIt() {
        assertThat(check.problems(subject("141f718e"), V2)).isEmpty();
    }

    @Test
    void theFixPermissionsPathsAreReadFromTheRunSteps() {
        assertThat(WritablePaths.fixedPermissions(List.of(new Subject.Step("RUN",
                "mkdir -p /a && /usr/libexec/fix-permissions /var/lib/pgsql && fix-permissions /var/run/x -P"))))
                .containsExactly("/var/lib/pgsql", "/var/run/x");
    }

    @Test
    void aHomeInTmpIsNotChecked() {
        assertThat(WritablePaths.written(new Subject("a", null, new Subject.Config(null, List.of(), null, null, null,
                Map.of("HOME", "/tmp")), List.of(), List.of()))).isEmpty();
    }

    @Test
    void aWorkingDirectoryMadeGroupWritableInTheFinalStageIsNot() {
        assertThat(check.problems(subject("08bf2be5"), V2)).isEmpty();
    }

    @Test
    void aGroupWriteCoversThePathItselfAndBelowItOnlyWhenRecursive() {
        Subject.Permission recursive = new Subject.Permission("chmod", "g+rwx", List.of("/opt"), true);
        Subject.Permission flat = new Subject.Permission("chmod", "775", List.of("/opt"), false);

        assertAll(
                () -> assertThat(WritablePaths.covers(recursive, "/opt/app/data")).isTrue(),
                () -> assertThat(WritablePaths.covers(flat, "/opt")).isTrue(),
                () -> assertThat(WritablePaths.covers(flat, "/opt/app")).isFalse(),
                () -> assertThat(WritablePaths.covers(recursive, "/optional")).isFalse());
    }

    @Test
    void groupWriteIsAModeOrSymbolicSettingThatGivesTheGroupWrite() {
        assertAll(
                () -> assertThat(WritablePaths.groupWrite("g=u")).isTrue(),
                () -> assertThat(WritablePaths.groupWrite("ug+rwx")).isTrue(),
                () -> assertThat(WritablePaths.groupWrite("a+w")).isTrue(),
                () -> assertThat(WritablePaths.groupWrite("0775")).isTrue(),
                () -> assertThat(WritablePaths.groupWrite("755")).isFalse(),
                () -> assertThat(WritablePaths.groupWrite("+x")).isFalse(),
                () -> assertThat(WritablePaths.groupWrite("g-w")).isFalse());
    }
}
