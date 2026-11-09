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

    @Test
    void theShippedProfilesSetEachRulesSeverityAndTurnOffThoseAPlatformCheckRepeats() {
        Profile v2 = Profile.shipped(Profile.DEFAULT);

        assertThat(v2.severity()).containsEntry(Check.S2, Severity.DRIFT).containsEntry(Check.S5, Severity.DRIFT)
                .containsEntry(Check.H3, Severity.INFORMATION)
                .containsEntry(Check.S4, Severity.OFF).containsEntry(Check.H2, Severity.OFF)
                .containsEntry(Check.R1, Severity.OFF);
    }

    @Test
    void aRuleTheProfileLeavesOutIsInformation() {
        Profile copied = Profile.of(FIXTURES.resolve("copied-profile.json").toString());

        assertThat(copied.severity()).containsEntry(Check.S2, Severity.INFORMATION);
    }

    @Test
    void anUnknownRuleOrAPlatformCheckAmongTheRulesIsRefusedByName() {
        String platform = """
                {"name": "x", "describes": "", "allowedRegistries": [], "severity": {"P1": "breaks", "P2": "breaks",
                 "P3": "breaks", "P4": "breaks", "P5": "breaks", "P6": "breaks", "P7": "breaks", "P8": "drift",
                 "P9": "information", "P10": "information"}, "rules": {"%s": "drift"}}""";
        assertAll(
                () -> assertThatThrownBy(() -> Profile.parse("x", platform.formatted("Z9")))
                        .hasMessage("profile x: Z9 is not a rule"),
                () -> assertThatThrownBy(() -> Profile.parse("x", platform.formatted("P1")))
                        .hasMessage("profile x: P1 is not a rule"));
    }
}
