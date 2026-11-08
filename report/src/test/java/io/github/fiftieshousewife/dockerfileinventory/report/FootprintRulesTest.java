package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FootprintRulesTest {

    @Test
    void theDefaultsClassifyEachBaseAsBefore() {
        assertThat(List.of("registry.access.redhat.com/ubi9/ubi-micro", "ubi9/ubi-minimal", "fedora-minimal",
                "busybox", "registry.access.redhat.com/ubi9/ubi", "debian", "ubuntu", "quay.io/centos/centos",
                "gcr.io/distroless/static", "scratch", "alpine", "debian:bookworm-slim",
                "registry.ci.openshift.org/ocp/5.0"))
                .map(FootprintRules.defaults()::classify).containsExactly("micro", "minimal", "minimal", "minimal",
                        "full", "full", "full", "full", "distroless", "scratch", "alpine", "slim", "unknown");
    }

    @Test
    void aPatternWithoutASlashMatchesTheImagesName() {
        FootprintRules rules = FootprintRules.of(List.of(new FootprintRules.Rule("go*", "", "full")));

        assertThat(rules.classify("docker.io/library/golang:1.22")).isEqualTo("full");
    }

    @Test
    void aPatternWithASlashMatchesThePathAndATagPatternTheTag() {
        FootprintRules rules = FootprintRules.of(List.of(new FootprintRules.Rule("ocp/*", "base-rhel*", "full")));

        assertAll(
                () -> assertThat(rules.classify("registry.ci.openshift.org/ocp/5.0:base-rhel9")).isEqualTo("full"),
                () -> assertThat(rules.classify("registry.ci.openshift.org/ocp/5.0:builder")).isEqualTo("unknown"));
    }

    @Test
    void anEstatesRulesComeBeforeTheDefaults(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("footprints.csv"), "image,tag,footprint\nubi-minimal,,full\n");

        assertThat(FootprintRules.read(file).then(FootprintRules.defaults()).classify("ubi9/ubi-minimal"))
                .isEqualTo("full");
    }

    @Test
    void aBadLineIsRefusedNamingItsFileAndLine(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("footprints.csv"), "image,tag,footprint\nubi-minimal,,tiny\n");

        assertThatIllegalArgumentException().isThrownBy(() -> FootprintRules.read(file))
                .withMessage(file + " line 2: tiny is not a footprint (micro, minimal, full, distroless, scratch, "
                        + "alpine, slim, unknown)");
    }
}
