package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class WritablePathsTest {

    private final WritablePaths check = new WritablePaths();

    @Test
    void aVolumeAndAHomeNeverMadeGroupWritableAreProblems() {
        assertThat(check.problems(subject("141f718e"), V2)).containsExactly(
                "VOLUME /var/lib/pgsql/data is never made writable by group 0 (chmod g=u or g+w), so OpenShift's "
                        + "arbitrary UID may not write it",
                "HOME /var/lib/pgsql is never made writable by group 0 (chmod g=u or g+w), so OpenShift's arbitrary "
                        + "UID may not write it");
    }

    @Test
    void aWorkingDirectoryMadeGroupWritableInTheFinalStageIsNot() {
        assertThat(check.problems(subject("08bf2be5"), V2)).isEmpty();
    }

    @Test
    void aGroupWriteCoversThePathItselfAndBelowItOnlyWhenRecursive() {
        Subject.Permission recursive = new Subject.Permission("chmod", "g+rwx", java.util.List.of("/opt"), true);
        Subject.Permission flat = new Subject.Permission("chmod", "775", java.util.List.of("/opt"), false);

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
