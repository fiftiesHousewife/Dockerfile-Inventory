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
    void theV1ProfileDiffersInTheSetuidCheckEscalationAndSeccomp() {
        Profile v1 = Profile.shipped("openshift-4-restricted");
        Profile v2 = Profile.shipped(Profile.DEFAULT);

        assertThat(v1.severity().entrySet()).filteredOn(e -> e.getValue() != v2.severity().get(e.getKey()))
                .containsExactlyInAnyOrder(Map.entry(Check.P5, Severity.INFORMATION),
                        Map.entry(Check.D4, Severity.OFF), Map.entry(Check.D6, Severity.OFF));
    }

    @Test
    void restrictedV2RefusesEveryPodCheckAndAllowsOnlyBindingLowPorts() {
        Profile v2 = Profile.shipped(Profile.DEFAULT);

        assertAll(
                () -> assertThat(v2.severity()).containsEntry(Check.D1, Severity.REFUSED)
                        .containsEntry(Check.D2, Severity.REFUSED).containsEntry(Check.D3, Severity.REFUSED)
                        .containsEntry(Check.D4, Severity.REFUSED).containsEntry(Check.D5, Severity.REFUSED)
                        .containsEntry(Check.D6, Severity.REFUSED),
                () -> assertThat(v2.allowedCapabilities()).containsExactly("NET_BIND_SERVICE"));
    }

    @Test
    void restrictedV1AllowsNoAddedCapability() {
        assertThat(Profile.shipped("openshift-4-restricted").allowedCapabilities()).isEmpty();
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
                .hasMessage("profile x: P99 is not a check (P1 to P11)");
    }

    @Test
    void aBadSeverityIsRefusedByName() {
        assertThatThrownBy(() -> Profile.parse("x", """
                {"name": "x", "describes": "", "allowedRegistries": [], "severity": {"P1": "fatal"}}"""))
                .hasMessage("profile x: P1 is fatal, not refused, breaks, drift, information or off");
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
                .containsEntry(Check.S6, Severity.DRIFT)
                .containsEntry(Check.H3, Severity.INFORMATION).containsEntry(Check.H4, Severity.INFORMATION)
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
                 "P9": "information", "P10": "information", "P11": "breaks"}, "pod": {"D1": "refused",
                 "D2": "refused", "D3": "refused", "D4": "refused", "D5": "refused", "D6": "refused",
                 "D7": "breaks"}, "rules": {"%s": "drift"}}""";
        assertAll(
                () -> assertThatThrownBy(() -> Profile.parse("x", platform.formatted("Z9")))
                        .hasMessage("profile x: Z9 is not a rule"),
                () -> assertThatThrownBy(() -> Profile.parse("x", platform.formatted("P1")))
                        .hasMessage("profile x: P1 is not a rule"));
    }

    private static final String WITH_POD = """
            {"name": "x", "describes": "", "allowedRegistries": [], "severity": {"P1": "breaks", "P2": "breaks",
             "P3": "breaks", "P4": "breaks", "P5": "breaks", "P6": "breaks", "P7": "breaks", "P8": "drift",
             "P9": "information", "P10": "information", "P11": "breaks"}, "pod": {POD},
             "allowedCapabilities": ["NET_BIND_SERVICE"]}""";

    private static String withPod(String checks) {
        return WITH_POD.replace("POD", checks);
    }

    @Test
    void aPodCheckLeftOutIsRefusedByName() {
        assertThatThrownBy(() -> Profile.parse("x", withPod("""
                "D1": "refused", "D2": "refused\"""")))
                .hasMessage("profile x: no severity for D3");
    }

    @Test
    void anUnknownPodCheckIsRefusedByName() {
        assertThatThrownBy(() -> Profile.parse("x", withPod("""
                "D9": "refused\"""")))
                .hasMessage("profile x: D9 is not a pod check (D1 to D7)");
    }

    @Test
    void thePodChecksAndTheAllowedCapabilitiesAreRead() {
        Profile profile = Profile.parse("x", withPod("""
                "D1": "refused", "D2": "refused", "D3": "refused", "D4": "off", "D5": "refused", "D6": "off",
                "D7": "breaks\""""));

        assertAll(
                () -> assertThat(profile.severity()).containsEntry(Check.D1, Severity.REFUSED)
                        .containsEntry(Check.D4, Severity.OFF),
                () -> assertThat(profile.allowedCapabilities()).containsExactly("NET_BIND_SERVICE"));
    }

    @Test
    void theSeverityWordsAreListedWithOrBeforeTheLast() {
        assertThat(Profile.words()).isEqualTo("refused, breaks, drift, information or off");
    }

    @Test
    void thePlatformChecksAreThePChecks() {
        assertThat(Profile.platform()).allMatch(check -> check.name().startsWith("P")).hasSize(11);
    }
}
