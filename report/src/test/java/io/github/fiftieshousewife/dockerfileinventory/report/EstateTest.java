package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class EstateTest {

    static final Path FIXTURES = Path.of("src/test/resources/fixtures/estate");

    private static final ObjectMapper JSON = new ObjectMapper();

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

    @Test
    void effectiveUsersComeFromEachDockerfilesNewestLinkedImageAndAreAbsentWithoutLineage() {
        assertThat(Estate.read(FIXTURES, FIXTURES, null).effectiveUsers()).isEmpty();
        assertThat(Estate.read(FIXTURES, FIXTURES, Path.of("src/test/resources/fixtures/lineage")).effectiveUsers())
                .containsExactly(Map.entry("blob-a", UserKind.NUMERIC));
    }

    @Test
    void theManifestGroupsLocationsByBlob() {
        assertThat(Estate.locations(FIXTURES.resolve("manifest.jsonl")).get("blob-a")).containsExactly(
                new Location("payments/api", "Dockerfile", "RELEASED"),
                new Location("payments/web", "build/Dockerfile", "DEFAULT_BRANCH"));
    }

    @Test
    void missingLocationsAreThoseOfBlobsNeitherParsedNorFailedSortedByRepositoryThenPath() {
        Location z = new Location("z/repo", "Dockerfile", "RELEASED");
        Location a2 = new Location("a/repo", "b/Dockerfile", "RELEASED");
        Location a1 = new Location("a/repo", "a/Dockerfile", "RELEASED");
        Map<String, List<Location>> locations = Map.of("parsed", List.of(new Location("p", "D", "RELEASED")),
                "failed", List.of(new Location("f", "D", "RELEASED")), "gone", List.of(z, a2, a1));

        assertThat(Estate.missing(locations, List.of(PassesTest.dockerfile("parsed", "t", "s", 1)),
                List.of(PassesTest.failure("failed", 1)))).containsExactly(a1, a2, z);
    }

    @Test
    void aFactsRowWithoutOptionalFieldsReadsAsEmpty() {
        JsonNode row = JSON.readTree("{\"blobSha\":\"b\",\"facts\":{\"stageCount\":1,\"base\":{}}}");

        assertThat(Estate.dockerfile(row, List.of())).satisfies(d -> {
            assertThat(d.base()).isEqualTo(new Base(null, null, null));
            assertThat(d.managers()).isEmpty();
            assertThat(d.finalPackages()).isEmpty();
        });
    }

    @Test
    void aMetadataOnlyBundleIsScratchWithNoRunAndNoStartCommand() {
        JsonNode bundle = JSON.readTree("""
                {"facts":{"base":{"name":"scratch"},"runs":0,"entrypointForm":"NONE"},"steps":[
                  {"keyword":"LABEL","text":"operators.operatorframework.io.bundle.mediatype.v1"},
                  {"keyword":"COPY","text":"/manifests/"},{"keyword":"COPY","text":"/metadata/"}]}
                """);

        assertThat(Estate.metadataOnly(bundle)).isTrue();
    }

    @Test
    void aScratchImageWithABinaryAndAnEntrypointIsNotABundle() {
        JsonNode binary = JSON.readTree("""
                {"facts":{"base":{"name":"scratch"},"runs":0,"entrypointForm":"EXEC"},"steps":[
                  {"keyword":"COPY","text":"/xds-control-plane"},
                  {"keyword":"ENTRYPOINT","text":"[\\"/xds-control-plane\\"]"}]}
                """);

        assertThat(Estate.metadataOnly(binary)).isFalse();
    }

    @Test
    void aCommandOrARunOrAnotherBaseMeansNotABundle() {
        assertThat(Estate.metadataOnly(JSON.readTree("""
                {"facts":{"base":{"name":"scratch"},"runs":0,"entrypointForm":"NONE"},
                 "steps":[{"keyword":"CMD","text":"/app"}]}"""))).isFalse();
        assertThat(Estate.metadataOnly(JSON.readTree("""
                {"facts":{"base":{"name":"scratch"},"runs":1,"entrypointForm":"NONE"},"steps":[]}"""))).isFalse();
        assertThat(Estate.metadataOnly(JSON.readTree("""
                {"facts":{"base":{"name":"alpine"},"runs":0,"entrypointForm":"NONE"},"steps":[]}"""))).isFalse();
    }

    @Test
    void eachDockerfileSaysWhetherItIsABundle() {
        JsonNode bundle = JSON.readTree("""
                {"blobSha":"b","facts":{"stageCount":1,"base":{"name":"scratch"},"runs":0,"entrypointForm":"NONE"}}
                """);

        assertThat(Estate.dockerfile(bundle, List.of()).bundle()).isTrue();
        assertThat(Estate.read(FIXTURES, FIXTURES, null).dockerfiles()).noneMatch(Dockerfile::bundle);
    }

    @Test
    void finalPackagesAreThoseInstalledInTheLastStageOnly() {
        JsonNode facts = JSON.readTree("""
                {"stageCount":2,"packageCalls":[
                  {"stage":0,"call":{"manager":"apk","packages":["build-base"]}},
                  {"stage":1,"call":{"manager":"apk","packages":["curl","ca-certificates"]}}]}
                """);

        assertThat(Estate.finalPackages(facts)).containsExactly("ca-certificates", "curl");
    }
}
