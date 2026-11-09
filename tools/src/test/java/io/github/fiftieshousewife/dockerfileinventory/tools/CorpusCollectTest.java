package io.github.fiftieshousewife.dockerfileinventory.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class CorpusCollectTest {

    private static final String FAILED = "f".repeat(40);
    private static final String TEMPLATE = "e".repeat(40);
    private static final String UNREADABLE = "d".repeat(40);
    private static final String ROUND_TRIP = "c".repeat(40);
    private static final String RETRIED = "b".repeat(40);
    private static final String SECRET = "a".repeat(40);

    @TempDir
    Path run;

    @TempDir
    Path corpus;

    @BeforeEach
    void aRun() throws IOException {
        Path blobs = Files.createDirectories(run.resolve("discovery/blobs"));
        for (String blob : new String[] {FAILED, TEMPLATE, UNREADABLE, ROUND_TRIP, RETRIED, SECRET}) {
            Files.writeString(blobs.resolve(blob), "FROM ubi9\nRUN echo " + blob.charAt(0) + "\n");
            Files.writeString(run.resolve("discovery/manifest.jsonl"), "{\"blobSha\":\"" + blob + "\",\"repository\":"
                    + "\"team/z\",\"path\":\"Dockerfile\",\"commitSha\":\"c2\"}\n{\"blobSha\":\"" + blob + "\","
                    + "\"repository\":\"team/a\",\"path\":\"Dockerfile\",\"commitSha\":\"c1\"}\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Files.writeString(blobs.resolve(RETRIED), "FROM ubi9\nARG VERSION=\n");
        Files.createDirectories(run.resolve("facts"));
        Files.writeString(run.resolve("facts/facts.jsonl"), String.join("\n",
                fact(UNREADABLE, false, 1), fact(ROUND_TRIP, false, 0), fact(RETRIED, true, 0), fact(SECRET, false, 2))
                + "\n");
        Files.writeString(run.resolve("facts/parse-failures.jsonl"), "{\"blobSha\":\"" + FAILED + "\",\"message\":"
                + "\"Syntax error\"}\n{\"blobSha\":\"" + TEMPLATE + "\",\"message\":\"a template, not a Dockerfile: "
                + "{{\"}\n");
        Files.createDirectories(run.resolve("converter"));
        Files.writeString(run.resolve("converter/roundtrip.jsonl"), "{\"blobSha\":\"" + ROUND_TRIP + "\",\"passed\""
                + ":false,\"difference\":\"line 2\"}\n{\"blobSha\":\"" + UNREADABLE + "\",\"passed\":true}\n");
        Files.createDirectories(run.resolve("rules"));
        Files.writeString(run.resolve("rules/findings.jsonl"), "{\"rule\":\"S2\",\"blobSha\":\"" + SECRET + "\"}\n");
    }

    private static String fact(String blob, boolean retried, int opaqueRuns) {
        return "{\"blobSha\":\"" + blob + "\",\"retried\":" + retried + ",\"facts\":{\"opaqueRuns\":" + opaqueRuns
                + "}}";
    }

    @Test
    void eachKindOfHardCaseIsCollectedWithItsOutcomeAndNoSecretIs() throws IOException {
        new CorpusCollect(run, corpus, 4, 5).collect();

        try (Stream<Path> cases = Files.list(corpus)) {
            assertThat(cases.map(c -> String.valueOf(c.getFileName()))).containsExactlyInAnyOrder("failed-ffffffff",
                    "template-eeeeeeee", "unreadable-run-dddddddd", "roundtrip-cccccccc",
                    "retried-arg-empty-bbbbbbbb");
        }
    }

    @Test
    void aCaseHoldsTheDockerfileAndItsExpectationFromItsFirstLocationInTheManifest() throws IOException {
        new CorpusCollect(run, corpus, 4, 5).collect();

        JsonNode expected = new ObjectMapper().readTree(corpus.resolve("unreadable-run-dddddddd/expected.json")
                .toFile());
        assertAll(
                () -> assertThat(corpus.resolve("unreadable-run-dddddddd/Dockerfile"))
                        .hasContent("FROM ubi9\nRUN echo d\n"),
                () -> assertThat(expected.path("source").path("repository").asString()).isEqualTo("team/z"),
                () -> assertThat(expected.path("why").asString()).isEqualTo("a RUN the shell reader cannot follow"),
                () -> assertThat(expected.path("parse").asString()).isEqualTo("parsed"),
                () -> assertThat(expected.path("unreadableRuns").asInt()).isEqualTo(1),
                () -> assertThat(expected.path("roundTrip").asString()).isEqualTo("passes"));
    }

    @Test
    void anExistingCaseIsKeptAsItIs() throws IOException {
        Files.createDirectories(corpus.resolve("failed-ffffffff"));
        Files.writeString(corpus.resolve("failed-ffffffff/expected.json"), "{\"kept\":true}");

        new CorpusCollect(run, corpus, 4, 5).collect();

        assertThat(corpus.resolve("failed-ffffffff/expected.json")).hasContent("{\"kept\":true}");
    }

    @Test
    void aRunMissingAnInputIsRefusedNamingIt() throws IOException {
        Files.delete(run.resolve("rules/findings.jsonl"));

        assertThatIllegalArgumentException().isThrownBy(() -> new CorpusCollect(run, corpus, 4, 5).collect())
                .withMessage("no " + run.resolve("rules/findings.jsonl"));
    }

    @Test
    void anExpectationIsWrittenAsTheBanksOtherCasesAreTwoSpacesNoSpaceBeforeAColon() throws IOException {
        new CorpusCollect(run, corpus, 4, 5).collect();

        assertThat(corpus.resolve("failed-ffffffff/expected.json")).content()
                .startsWith("{\n  \"source\": {\n    \"repository\": \"team/z\",");
    }
}
