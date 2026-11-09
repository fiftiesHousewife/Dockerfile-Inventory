package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import org.junit.jupiter.api.Test;

class PinDriftTest {

    private static String digest(String hex) {
        return "sha256:" + hex.repeat(64);
    }

    private static CatalogueView.Entry entry(String sha, String name, String tag, String digest) {
        return new CatalogueView.Entry(sha, List.of(new CatalogueView.Pinned(name, tag, digest)));
    }

    @Test
    void r4ReportsEachDockerfilePinningABaseAndTagTheEstatePinsAtSeveralDigests() {
        assertThat(new PinDrift().check(List.of(
                entry("a", "ubi9/ubi-minimal", "latest", digest("1")),
                entry("b", "ubi9/ubi-minimal", "latest", digest("2")),
                entry("c", "ubi9/ubi-minimal", "latest", digest("2")),
                entry("d", "golang", "1.24", digest("3")),
                entry("e", "golang", "1.25", digest("4")))))
                .extracting(Finding::blobSha, Finding::message).containsExactly(
                        tuple("a", "pins ubi9/ubi-minimal:latest at one of 2 digests the estate's 3 Dockerfiles pin "
                                + "it at"),
                        tuple("b", "pins ubi9/ubi-minimal:latest at one of 2 digests the estate's 3 Dockerfiles pin "
                                + "it at"),
                        tuple("c", "pins ubi9/ubi-minimal:latest at one of 2 digests the estate's 3 Dockerfiles pin "
                                + "it at"));
    }

    @Test
    void aBaseWithoutATagIsNamedByItsNameAlone() {
        assertThat(new PinDrift().check(List.of(entry("a", "alpine", null, digest("1")),
                entry("b", "alpine", null, digest("2")))).getFirst().message())
                .startsWith("pins alpine at one of 2 digests");
    }

    @Test
    void aPlaceholderWhereADigestShouldBeIsNoPin() {
        assertThat(new PinDrift().check(List.of(entry("a", "ocp", null, "sha256..."),
                entry("b", "ocp", null, "sha256:" + "a".repeat(64))))).isEmpty();
    }
}
