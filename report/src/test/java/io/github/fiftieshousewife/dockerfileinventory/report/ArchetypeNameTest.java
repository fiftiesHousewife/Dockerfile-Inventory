package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ArchetypeNameTest {

    @Test
    void aNameIsTheBaseFamilyTheManagersWithTheirFirstPackagesAndTheUser() {
        assertThat(ArchetypeName.of("minideb:|apt-get|ca-certificates,curl,procps|NUMERIC||EXEC|stages=1"))
                .isEqualTo("minideb with apt-get (ca-certificates, curl, procps), numeric user");
        assertThat(ArchetypeName.of(
                "s2i-base-c9s:9|yum,pip|hostname,mod_ssl,php,php-fpm,php-gd,tzdata|NAMED|8080,8443|EXEC|stages=1"))
                .isEqualTo("s2i-base-c9s 9 with yum and pip (hostname, mod_ssl, php and 3 more), named user");
    }

    @Test
    void withoutPackagesTheNameSaysSoAndCountsStagesAboveOne() {
        assertThat(ArchetypeName.of("ubi-minimal:|||ROOT||EXEC|stages=2"))
                .isEqualTo("ubi-minimal, no packages, root, 2 stages");
        assertThat(ArchetypeName.of("alpine:3|||UNRESOLVED||NONE|stages=1"))
                .isEqualTo("alpine 3, no packages, user from a variable");
    }

    @Test
    void templatesUnreadableFilesUnresolvedBasesAndBundlesHaveTheirOwnNames() {
        assertThat(ArchetypeName.of(Passes.TEMPLATES)).isEqualTo("template, not a Dockerfile");
        assertThat(ArchetypeName.of(Passes.PARSE_FAILURES)).isEqualTo("could not be parsed");
        assertThat(ArchetypeName.of("unresolved:|||ROOT||NONE|stages=1"))
                .isEqualTo("base set by a variable, no packages, root");
        assertThat(ArchetypeName.BUNDLE).isEqualTo("bundle (scratch, metadata only)");
    }

    @Test
    void theSignatureReadsAsATableOfAttributes() {
        assertThat(ArchetypeName.attributes("golang:1|apt-get|jq|ROOT|8080,8443|SHELL|stages=2")).containsExactly(
                Map.entry("Base family", "golang 1"), Map.entry("Package managers", "apt-get"),
                Map.entry("Packages", "jq"), Map.entry("User", "root"), Map.entry("Ports", "8080, 8443"),
                Map.entry("Start command", "shell form"), Map.entry("Stages", "2"));
        assertThat(ArchetypeName.attributes("scratch:|||NUMERIC||NONE|stages=1")).containsExactly(
                Map.entry("Base family", "scratch"), Map.entry("Package managers", "none"),
                Map.entry("Packages", "none"), Map.entry("User", "numeric user"), Map.entry("Ports", "none"),
                Map.entry("Start command", "none of its own"), Map.entry("Stages", "1"));
    }

    @Test
    void theFirstThreePackagesAreNamedAndTheRestCounted() {
        assertAll(
                () -> assertThat(ArchetypeName.packages("git,curl,jq,tar,gzip")).isEqualTo("git, curl, jq and 2 more"),
                () -> assertThat(ArchetypeName.packages("git,curl")).isEqualTo("git, curl"));
    }

    @Test
    void anEmptyAttributeReadsAsNone() {
        assertAll(
                () -> assertThat(ArchetypeName.orNone("")).isEqualTo("none"),
                () -> assertThat(ArchetypeName.orNone("8080")).isEqualTo("8080"));
    }

    @Test
    void theStartCommandSaysItsForm() {
        assertAll(
                () -> assertThat(ArchetypeName.start("EXEC")).isEqualTo("exec form"),
                () -> assertThat(ArchetypeName.start("SHELL")).isEqualTo("shell form"),
                () -> assertThat(ArchetypeName.start("NONE")).isEqualTo("none of its own"));
    }
}
