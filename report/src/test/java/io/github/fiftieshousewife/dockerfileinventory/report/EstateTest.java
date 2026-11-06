package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EstateTest {

    static final Path FIXTURES = Path.of("src/test/resources/fixtures/estate");

    @Test
    void readsEachBlobsFactsHashesAndSignature() {
        Estate estate = Estate.read(FIXTURES, FIXTURES, null);

        assertThat(estate.dockerfiles()).extracting(Dockerfile::blobSha).containsExactly("blob-a", "blob-b");
        assertThat(estate.dockerfiles().getFirst()).satisfies(a -> {
            assertThat(a.signature()).isEqualTo("ubi-minimal:9|microdnf|git,tar|NUMERIC|8080|EXEC|stages=1");
            assertThat(a.textHash()).startsWith("22a1ae83");
            assertThat(a.base()).isEqualTo(new Base("registry.example.com/ubi9/ubi-minimal", "9.4",
                    "sha256:" + "a".repeat(64)));
            assertThat(a.finalPackages()).containsExactly("git", "tar");
            assertThat(a.managers()).containsExactly("microdnf");
            assertThat(a.userKind()).isEqualTo("NUMERIC");
            assertThat(a.runs()).isEqualTo(1);
            assertThat(a.opaqueRuns()).isZero();
        });
    }

    @Test
    void eachBlobsLocationsComeFromTheManifestAndUnparsedBlobsAreReportedMissing() {
        Estate estate = Estate.read(FIXTURES, FIXTURES, null);

        assertThat(estate.dockerfiles().getFirst().locations()).containsExactly(
                new Location("payments/api", "Dockerfile", "RELEASED"),
                new Location("payments/web", "build/Dockerfile", "DEFAULT_BRANCH"));
        assertThat(estate.missing()).containsExactly(new Location("platform/gone", "Dockerfile", "RELEASED"));
    }

    @Test
    void parseFailuresAreReadWithTheirLocations() {
        assertThat(Estate.read(FIXTURES, FIXTURES, null).failures()).singleElement().satisfies(failure -> {
            assertThat(failure.blobSha()).isEqualTo("blob-bad");
            assertThat(failure.message()).startsWith("Syntax error");
            assertThat(failure.locations()).containsExactly(new Location("platform/old", "Dockerfile", "RELEASED"));
        });
    }
}
