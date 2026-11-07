package io.github.fiftieshousewife.dockerfileinventory.facts;

import static io.github.fiftieshousewife.dockerfileinventory.facts.FactExtractorTest.facts;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        String sameKind = APP
                .replace("FROM ubi9/ubi-minimal:9.4", "FROM registry.example.com/ubi9/ubi-minimal:9.6-1100")
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "registry.example.com:5000/ubi9/s2i-base | ubi9/s2i-base",
        "localhost/team/app                      | team/app",
        "artifactory:8443/team/app               | team/app",
        "ubi9/s2i-base                           | ubi9/s2i-base",
        "docker-local/team/app                   | docker-local/team/app",
        "${BASE}                                 | -",
    })
    void theFromPathDropsARegistryHostButKeepsAFirstSegmentThatIsNotOne(String name, String path) {
        assertThat(NormalForms.fromPath(new BaseImage(name, "1", null))).isEqualTo(path);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "registry.example.com/ubi9/ubi-minimal | ubi-minimal",
        "${BASE}                               | unresolved",
    })
    void familyIsTheLastPathSegmentOrUnresolved(String name, String family) {
        assertThat(NormalForms.family(new BaseImage(name, null, null))).isEqualTo(family);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
        "9.4-1227 | 9",
        "latest   | ''",
        "-        | ''",
    })
    void majorIsTheTagsLeadingDigits(String tag, String major) {
        assertThat(NormalForms.major(new BaseImage("ubi9", tag, null))).isEqualTo(major);
    }

    @Test
    void upperKeywordUpperCasesOnlyTheFirstWord() {
        assertThat(NormalForms.upperKeyword("run make all")).isEqualTo("RUN make all");
        assertThat(NormalForms.upperKeyword("onbuild")).isEqualTo("ONBUILD");
    }

    @Test
    void textNormalDropsCommentsAndBlankLinesJoinsContinuationsAndCollapsesWhitespace() {
        assertThat(NormalForms.textNormal("# c\r\nfrom  a\n\nrun x \\\n   y\n")).isEqualTo("FROM a\nRUN x y");
    }

    @Test
    void sha256IsTheHexDigest() {
        assertThat(NormalForms.sha256(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }
}
