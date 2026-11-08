package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    @Test
    void theDefaultProfileIsRestrictedV2WithEveryCheckOnAndAnyRegistryAllowed() {
        Profile v2 = Profile.shipped(Profile.DEFAULT);

        assertAll(
                () -> assertThat(v2.name()).isEqualTo("openshift-4-restricted-v2"),
                () -> assertThat(v2.allowedRegistries()).isEmpty(),
                () -> assertThat(v2.severity()).containsEntry(Check.P5, Severity.BREAKS)
                        .containsEntry(Check.P8, Severity.DRIFT).containsEntry(Check.P9, Severity.INFORMATION)
                        .hasSize(Check.values().length));
    }

    @Test
    void theV1ProfileDiffersOnlyInTheSetuidCheck() {
        Profile v1 = Profile.shipped("openshift-4-restricted");
        Profile v2 = Profile.shipped(Profile.DEFAULT);

        assertThat(v1.severity().entrySet()).filteredOn(e -> e.getValue() != v2.severity().get(e.getKey()))
                .containsExactly(Map.entry(Check.P5, Severity.INFORMATION));
    }

    @Test
    void aCopiedProfileIsReadFromItsFile() {
        Profile copied = Profile.of(FIXTURES.resolve("copied-profile.json").toString());

        assertAll(
                () -> assertThat(copied.allowedRegistries()).containsExactly("registry.acme.example"),
                () -> assertThat(copied.severity()).containsEntry(Check.P8, Severity.BREAKS));
    }

    @Test
    void anUnknownCheckIsRefusedByName() {
        assertThatThrownBy(() -> Profile.parse("x", """
                {"name": "x", "describes": "", "allowedRegistries": [], "severity": {"P99": "breaks"}}"""))
                .hasMessage("profile x: P99 is not a check (P1 to P10)");
    }

    @Test
    void aBadSeverityIsRefusedByName() {
        assertThatThrownBy(() -> Profile.parse("x", """
                {"name": "x", "describes": "", "allowedRegistries": [], "severity": {"P1": "fatal"}}"""))
                .hasMessage("profile x: P1 is fatal, not breaks, drift, information or off");
    }

    @Test
    void aCheckLeftOutIsRefusedByName() {
        assertThatThrownBy(() -> Profile.parse("x", """
                {"name": "x", "describes": "", "allowedRegistries": [], "severity": {"P1": "breaks"}}"""))
                .hasMessageStartingWith("profile x: no severity for P2");
    }

    @Test
    void aProfileThatIsNeitherShippedNorAFileIsRefusedByName() {
        assertThatThrownBy(() -> Profile.of("openshift-5"))
                .hasMessage("PROFILE openshift-5 is neither a shipped profile (openshift-4-restricted-v2, "
                        + "openshift-4-restricted) nor a file");
    }
}
