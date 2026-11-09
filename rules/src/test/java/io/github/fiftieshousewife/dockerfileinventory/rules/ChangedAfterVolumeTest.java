package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChangedAfterVolumeTest {

    @Test
    void h7ReportsAPermissionChangeInsideAVolumeDeclaredEarlierInTheFinalImage() {
        assertThat(new ChangedAfterVolume().check(Subjects.of("""
                FROM ubi9
                RUN chown -R 1001:0 /data
                VOLUME ["/data", "/logs"]
                RUN chgrp -R 0 /data/db && chmod g=u /logs /etc/app
                RUN chmod 755 /usr/local/bin/app
                """))).extracting(Finding::line, Finding::message).containsExactly(
                        tuple(4, "chgrp -R 0 /data/db changes the volume /data declared on line 3"),
                        tuple(4, "chmod g=u /logs /etc/app changes the volume /logs declared on line 3"));
    }

    @Test
    void h7IgnoresAFileThatDidNotParse() {
        assertThat(new ChangedAfterVolume().check(Subject.failed("blob", "Syntax error"))).isEmpty();
    }

    @Test
    void aVolumesPathsAreReadInEitherForm() {
        assertAll(
                () -> assertThat(ChangedAfterVolume.paths("VOLUME /data /logs")).isEqualTo(List.of("/data", "/logs")),
                () -> assertThat(ChangedAfterVolume.paths("VOLUME [\"/data\", \"/var/lib/app\"]"))
                        .isEqualTo(List.of("/data", "/var/lib/app")));
    }
}
