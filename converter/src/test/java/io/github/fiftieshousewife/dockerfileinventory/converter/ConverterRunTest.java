package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Policy;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ConverterRunTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path discovery;

    @TempDir
    Path out;

    @TempDir
    Path lists;

    @BeforeEach
    void estate() throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/good"), """
                FROM ubi9/ubi-minimal:9.4
                RUN microdnf install -y git && microdnf clean all
                USER 1001
                HEALTHCHECK NONE
                """);
        Files.writeString(discovery.resolve("blobs/elsewhere"), "FROM ubi9/ubi-minimal:9.4\nUSER 1001\n");
        Files.writeString(discovery.resolve("blobs/odd-digest"), "FROM ubi9@${DIGEST}\n");
        Files.writeString(discovery.resolve("blobs/bad"), "not a dockerfile\n");
        Files.writeString(discovery.resolve("manifest.jsonl"), String.join("\n",
                location("payments/api", "Dockerfile", "good"),
                location("platform/tools", "build/Dockerfile", "elsewhere"),
                location("payments/web", "Dockerfile", "odd-digest"),
                location("legacy/old", "Dockerfile", "bad")) + "\n");
        Files.writeString(lists.resolve("opt-in.txt"), "payments/api\npayments/web\n");
    }

    @Test
    void passingDockerfilesInOptedInRepositoriesGetAClassAndFailuresARowWithTheirDifference() throws IOException {
        new ConverterRun(discovery, lists.resolve("opt-in.txt"), null, out).run();

        List<JsonNode> rows = Files.readAllLines(out.resolve("roundtrip.jsonl")).stream().map(JSON::readTree).toList();
        String generated = Files.readString(out.resolve("generated/images/payments/PaymentsApiImage.java"));
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(generated).contains("package images.payments;")
                    .contains(".install(PackageManager.MICRODNF");
            softly.assertThat(out.resolve("generated/images/platform")).doesNotExist();
            softly.assertThat(rows).extracting(r -> r.path("blobSha").asString() + "=" + r.path("passed").asBoolean())
                    .containsExactlyInAnyOrder("good=true", "elsewhere=true", "odd-digest=false", "bad=false");
            softly.assertThat(rows).filteredOn(r -> r.path("blobSha").asString().equals("odd-digest"))
                    .singleElement().satisfies(r -> assertThat(r.path("difference").asString()).contains("digest"));
        });
    }

    @Test
    void coverageIsTypedInstructionsOverAllPerImageWithAHistogramOfRawReasons() {
        new ConverterRun(discovery, null, null, out).run();

        JsonNode coverage = JSON.readTree(out.resolve("coverage.json").toFile());
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(coverage.path("images").path("good").asDouble()).isEqualTo(2.0 / 3);
            softly.assertThat(coverage.path("images").path("elsewhere").asDouble()).isEqualTo(1.0);
            softly.assertThat(coverage.path("rawReasons").path("HEALTHCHECK is not modelled").asInt()).isEqualTo(1);
            softly.assertThat(out.resolve("generated")).doesNotExist();
        });
    }

    @Test
    void aDockerfileThatDoesNotParseIsARowSayingSo() {
        ConverterRun run = new ConverterRun(discovery, null, null, out);

        ConverterRun.Row row = run.row(new FactsParser(), discovery.resolve("blobs/bad"), new Coverage(),
                new GeneratedClasses(Set.of(), Map.of(), Policy.of(List.of()), false, new RunFiles(discovery, out)));

        assertThat(row).isEqualTo(new ConverterRun.Row("bad", false, "the Dockerfile does not parse"));
    }

    @Test
    void jsonLinesIsOneObjectPerLineEndingInANewlineUnlessThereAreNone() {
        ConverterRun run = new ConverterRun(discovery, null, null, out);

        assertThat(run.jsonLines(List.of(new ConverterRun.Row("a", true, null))))
                .isEqualTo("{\"blobSha\":\"a\",\"passed\":true,\"difference\":null}\n");
        assertThat(run.jsonLines(List.of())).isEmpty();
    }

    private static String location(String repository, String path, String blob) {
        return ("{\"repository\":\"%s\",\"commitSha\":\"c\",\"scope\":\"RELEASED\",\"path\":\"%s\","
                + "\"blobSha\":\"%s\"}").formatted(repository, path, blob);
    }
}
