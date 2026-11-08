package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class StagePermissionsTest {

    @Test
    void eachChmodChownAndChgrpInARunIsReadWithItsSettingPathsAndWhetherRecursive() {
        assertThat(StagePermissions.of("mkdir -p /opt/app && chgrp -R 0 /opt/app /var/log/app"
                + " && chmod -R g=u /opt/app && chown 1001:0 /etc/app.conf && chmod 777 /tmp/x"))
                .containsExactly(
                        new PermissionChange("chgrp", "0", List.of("/opt/app", "/var/log/app"), true),
                        new PermissionChange("chmod", "g=u", List.of("/opt/app"), true),
                        new PermissionChange("chown", "1001:0", List.of("/etc/app.conf"), false),
                        new PermissionChange("chmod", "777", List.of("/tmp/x"), false));
    }

    @Test
    void aRunTheShellReaderCannotReadHasNoChangesReadFromIt() {
        assertThat(StagePermissions.of("chmod 755 \"/a")).isEmpty();
    }

    @Test
    void recursionIsAnRInShortOptionsOrRecursiveSpelledOut() {
        assertAll(
                () -> assertThat(StagePermissions.change(List.of("chown", "-Rf", "1001", "/a")).recursive()).isTrue(),
                () -> assertThat(StagePermissions.change(List.of("chmod", "--recursive", "g=u", "/a")).recursive())
                        .isTrue(),
                () -> assertThat(StagePermissions.change(List.of("chmod", "-v", "644", "/a")).recursive()).isFalse());
    }
}
