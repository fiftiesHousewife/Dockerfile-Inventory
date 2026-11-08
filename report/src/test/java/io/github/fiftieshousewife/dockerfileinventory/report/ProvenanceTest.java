package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProvenanceTest {

    @Test
    void cosignsSiblingTagsSayAnImageIsSignedAttestedOrHasAnSbom() {
        assertThat(Provenance.of(List.of("sha256:abc"), Set.of("1.0", "sha256-abc.sig", "sha256-abc.att")))
                .isEqualTo(new Provenance(true, true, false));
    }

    @Test
    void anImageWithoutSiblingTagsHasNone() {
        assertThat(Provenance.of(List.of("sha256:abc"), Set.of("1.0", "sha256-def.sig")))
                .isEqualTo(new Provenance(false, false, false));
    }

    @Test
    void anyOfAnImagesManifestDigestsMayBeTheSignedOne() {
        assertThat(Provenance.of(List.of("sha256:abc", "sha256:def"), Set.of("sha256-def.sbom")))
                .isEqualTo(new Provenance(false, false, true));
    }
}
