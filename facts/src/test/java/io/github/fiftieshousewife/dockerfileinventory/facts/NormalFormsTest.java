package io.github.fiftieshousewife.dockerfileinventory.facts;

import static io.github.fiftieshousewife.dockerfileinventory.facts.FactExtractorTest.facts;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalFormsTest {

    private static final String ORIGINAL = """
            # Payments API image
            FROM ubi9/ubi-minimal:9.4

            RUN microdnf install -y git tar \\
                && microdnf clean all
            USER 1001
            """;

    @Test
    void hashesCosmeticVariantsEqually() {
        String variant = """
                from   ubi9/ubi-minimal:9.4
                # installs tools
                run microdnf install -y git tar && microdnf clean all
                user 1001
                """;

        assertThat(NormalForms.textHash(variant)).isEqualTo(NormalForms.textHash(ORIGINAL));
    }

    @Test
    void hashesDifferentInstructionsDifferently() {
        assertThat(NormalForms.textHash(ORIGINAL.replace("USER 1001", "USER 1002")))
                .isNotEqualTo(NormalForms.textHash(ORIGINAL));
    }

    private static final String APP = """
            FROM ubi9/ubi-minimal:9.4
            RUN microdnf install -y git tar && microdnf clean all
            USER 1001
            EXPOSE 8080
            ENTRYPOINT ["/opt/run"]
            """;

    @Test
    void sharesASignatureAcrossRegistryMinorTagPackageOrderAndUid() {
        String sameKind = APP.replace("FROM ubi9/ubi-minimal:9.4", "FROM registry.example.com/ubi9/ubi-minimal:9.6-1100")
                .replace("git tar", "tar git").replace("USER 1001", "USER 1002");

        assertThat(NormalForms.signature(facts(sameKind))).isEqualTo(NormalForms.signature(facts(APP)));
    }

    @Test
    void separatesADifferentPackageSet() {
        assertThat(NormalForms.signature(facts(APP.replace("git tar", "git tar curl"))))
                .isNotEqualTo(NormalForms.signature(facts(APP)));
    }

    @Test
    void separatesADifferentUserKind() {
        assertThat(NormalForms.signature(facts(APP.replace("USER 1001", "USER appuser"))))
                .isNotEqualTo(NormalForms.signature(facts(APP)));
    }

    @Test
    void separatesADifferentMajorVersion() {
        assertThat(NormalForms.signature(facts(APP.replace("ubi-minimal:9.4", "ubi-minimal:10.0"))))
                .isNotEqualTo(NormalForms.signature(facts(APP)));
    }
}
