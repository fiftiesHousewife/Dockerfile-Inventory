package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class RulesRunTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path discovery;

    @TempDir
    Path out;

    @BeforeEach
    void estate() throws IOException {
        Files.createDirectories(discovery.resolve("blobs"));
        Files.writeString(discovery.resolve("blobs/a"), "FROM ubi9/ubi-minimal:9.4\nRUN curl -s https://x | sh\n");
        Files.writeString(discovery.resolve("blobs/secret"), "FROM ubi9/ubi-minimal:9.4@sha256:" + "a".repeat(64)
                + "\nENV DB_PASSWORD=hunter2\n"
                + "LABEL org.opencontainers.image.revision=1 org.opencontainers.image.source=2\n");
        Files.writeString(discovery.resolve("blobs/bad"), "not a dockerfile\n");
        Files.writeString(discovery.resolve("blobs/half.part"), "FROM ubi9\n");
        Files.writeString(discovery.resolve("manifest.jsonl"), String.join("\n",
                location("payments/api", "a"), location("payments/web", "a"), location("platform/base", "a"),
                location("platform/tools", "secret"), location("legacy/old", "bad")) + "\n");
    }

    @Test
    void writesFindingsPerDockerfileAndSkipsRulesWhoseInputsAreMissing() throws IOException {
        new RulesRun(discovery, null, null, null, null, out).run();

        assertThat(lines("findings.jsonl"))
                .extracting(f -> f.path("rule").asString() + "@" + f.path("blobSha").asString())
                .contains("R1@a", "S3@a", "R3@a", "S2@secret", "I2@bad")
                .doesNotContain("R1@secret", "R3@secret");
        JsonNode rates = JSON.readTree(out.resolve("rates.json").toFile());
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(rates.path("skipped")).extracting(JsonNode::asString)
                    .containsExactly("R2", "R4", "S1", "S4");
            softly.assertThat(rates.path("rules").path("S3").path("findings").asInt()).isEqualTo(1);
            softly.assertThat(rates.path("rules").path("S3").path("locations").asInt()).isEqualTo(3);
        });
    }

    @Test
    void noCsvTableOrDdlIsWritten() {
        new RulesRun(discovery, null, null, null, null, out).run();

        assertThat(out.resolve("tables")).doesNotExist();
    }

    @Test
    void samplesSpreadAcrossOrganisationsAndAreTheSameEveryRun() throws IOException {
        new RulesRun(discovery, null, null, null, null, out).run();
        List<String> first = Files.readAllLines(out.resolve("samples/S3.csv"));
        new RulesRun(discovery, null, null, null, null, out).run();

        assertThat(Files.readAllLines(out.resolve("samples/S3.csv"))).isEqualTo(first);
        assertThat(first.subList(1, 3)).extracting(line -> line.split(",")[2].split("/")[0])
                .containsExactlyInAnyOrder("payments", "platform");
    }

    @Test
    void noSecretValueAppearsInAnyOutput() throws IOException {
        new RulesRun(discovery, null, null, null, null, out).run();

        try (Stream<Path> files = Files.walk(out)) {
            assertThat(files.filter(Files::isRegularFile).toList()).isNotEmpty()
                    .allSatisfy(file -> assertThat(Files.readString(file)).as(file.toString())
                            .doesNotContain("hunter2"));
        }
    }

    @Test
    void withLineageOutputTheEffectiveRulesRunOverEveryImage(@TempDir Path lineage) throws IOException {
        Files.writeString(lineage.resolve("effective.jsonl"), """
                {"configDigest":"img","chain":[{"configDigest":"img","link":{"tier":"STRONG","blobShas":["a"]}}],\
                "user":{"value":null,"setBy":{"kind":"Unknown"}}}
                """);
        Files.writeString(lineage.resolve("images.jsonl"), """
                {"repository":"docker-local","image":"payments/api","tag":"1","configDigest":"img"}
                """);

        new RulesRun(discovery, lineage, null, null, null, out).run();

        assertThat(lines("findings.jsonl")).filteredOn(f -> f.path("rule").asString().equals("S4"))
                .singleElement().satisfies(f -> assertThat(f.path("blobSha").asString()).isEqualTo("a"));
        assertThat(JSON.readTree(out.resolve("rates.json").toFile()).path("skipped"))
                .extracting(JsonNode::asString).containsExactly("R2", "R4", "S1");
    }

    @Test
    void withTheCatalogueTheRulesOverTheWholeEstateRun(@TempDir Path catalogue) throws IOException {
        Files.createDirectories(catalogue.resolve("catalogue"));
        Files.writeString(catalogue.resolve("catalogue/dockerfiles.jsonl"), pinned("a", "1") + pinned("secret", "2"));

        new RulesRun(discovery, null, null, null, catalogue, out).run();

        assertThat(lines("findings.jsonl"))
                .extracting(f -> f.path("rule").asString() + "@" + f.path("blobSha").asString()).contains("R4@a");
    }

    @Test
    void withBothListsTheirRulesRun() throws IOException {
        Path allow = Path.of("src/test/resources/fixtures/allow.csv");
        Path eol = Path.of("src/test/resources/fixtures/eol.csv");

        new RulesRun(discovery, null, allow, eol, null, out).run();

        assertThat(JSON.readTree(out.resolve("rates.json").toFile()).path("skipped"))
                .extracting(JsonNode::asString).containsExactly("R4", "S4");
    }

    @Test
    void listIsReadOnlyWhenAPathIsGiven() {
        assertThat(RulesRun.list(null)).isNull();
        assertThat(RulesRun.list(Path.of("src/test/resources/fixtures/allow.csv")).lookup("ubi9/ubi", "9"))
                .isPresent();
    }

    @Test
    void effectiveRunsEveryRuleOverEachImageInTurn() {
        EffectiveRule always = new EffectiveRule() {
            @Override
            public String id() {
                return "E1";
            }

            @Override
            public Severity severity() {
                return Severity.LOW;
            }

            @Override
            public List<Finding> check(ImageView image, Function<String, String> blobText) {
                return List.of(new Finding(id(), severity(), image.configDigest(), null, blobText.apply("x"), null,
                        List.of()));
            }
        };
        List<ImageView> images = List.of(new ImageView("one", null, null, List.of(), List.of()),
                new ImageView("two", null, null, List.of(), List.of()));

        assertThat(RulesRun.effective(List.of(always), images, sha -> "text of " + sha))
                .extracting(Finding::blobSha, Finding::message)
                .containsExactly(tuple("one", "text of x"), tuple("two", "text of x"));
    }

    @Test
    void jsonLinesWritesOneFindingPerLineEndingInANewlineOrNothingForNone() {
        RulesRun run = new RulesRun(discovery, null, null, null, null, out);
        Finding finding = new Finding("H2", Severity.LOW, "a", 3, "m", null, List.of("b"));

        assertThat(run.jsonLines(List.of(finding))).isEqualTo("{\"rule\":\"H2\",\"severity\":\"LOW\",\"blobSha\":\"a\","
                + "\"line\":3,\"message\":\"m\",\"valueHash\":null,\"contributing\":[\"b\"]}\n");
        assertThat(run.jsonLines(List.of())).isEmpty();
    }

    private List<JsonNode> lines(String file) throws IOException {
        return Files.readAllLines(out.resolve(file)).stream().map(JSON::readTree).toList();
    }

    private static String location(String repository, String blob) {
        return ("{\"repository\":\"%s\",\"commitSha\":\"c\",\"scope\":\"RELEASED\","
                + "\"path\":\"Dockerfile\",\"blobSha\":\"%s\"}").formatted(repository, blob);
    }

    private static String pinned(String blob, String digest) {
        return "{\"blobSha\":\"" + blob + "\",\"build\":{\"stages\":[{\"baseName\":\"ubi9\",\"baseTag\":\"9\","
                + "\"baseDigest\":\"sha256:" + digest.repeat(64) + "\"}]}}\n";
    }
}
