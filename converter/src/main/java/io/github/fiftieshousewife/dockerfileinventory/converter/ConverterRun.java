package io.github.fiftieshousewife.dockerfileinventory.converter;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.fiftieshousewife.dockerfile.model.Policy;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

final class ConverterRun {

    record Row(String blobSha, boolean passed, String difference,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean javaPassed,
            @JsonInclude(JsonInclude.Include.NON_NULL) String javaDifference) {

        Row withJava(RoundTrip java) {
            return new Row(blobSha, passed, difference, java.passed(), java.difference());
        }
    }

    private final RunFiles files;
    private final Path optIn;
    private final Path allowList;
    private final boolean javaRoundTrip;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, JavaRoundTrip.Candidate> candidates = new TreeMap<>();

    ConverterRun(Path discoveryDir, Path optIn, Path allowList, Path outDir, boolean javaRoundTrip) {
        this.files = new RunFiles(discoveryDir, outDir);
        this.optIn = optIn;
        this.allowList = allowList;
        this.javaRoundTrip = javaRoundTrip;
    }

    void run() {
        Set<String> optedIn = optIn == null ? Set.of() : GeneratedClasses.optedIn(RunFiles.read(optIn));
        Policy policy = allowList == null ? Policy.of(List.of()) : Policy.read(allowList);
        GeneratedClasses generated =
                new GeneratedClasses(optedIn, files.locations(json), policy, allowList != null, files);
        Coverage coverage = new Coverage();
        FactsParser parser = new FactsParser();
        List<Row> rows = new ArrayList<>();
        for (Path blob : files.blobs()) {
            rows.add(row(parser, blob, coverage, generated));
        }
        if (javaRoundTrip) {
            rows = withJava(rows, JavaRoundTrip.check(candidates), coverage);
        }
        files.write("roundtrip.jsonl", jsonLines(rows));
        files.write("coverage.json", coverage.json(json));
    }

    Row row(FactsParser parser, Path blob, Coverage coverage, GeneratedClasses generated) {
        String sha = String.valueOf(blob.getFileName());
        String text = RunFiles.read(blob);
        if (!(parser.parse(sha, text) instanceof ParseResult.Parsed parsed)) {
            return new Row(sha, false, "the Dockerfile does not parse", null, null);
        }
        Conversion conversion;
        try {
            conversion = Convert.of(parsed.file());
        } catch (IllegalArgumentException e) {
            return new Row(sha, false, "conversion failed: " + e.getMessage(), null, null);
        }
        coverage.add(sha, conversion);
        RoundTrip result = RoundTrip.check(text, conversion.model());
        if (result.passed()) {
            generated.generate(sha, conversion);
            candidates.put(sha, new JavaRoundTrip.Candidate(text, conversion));
        }
        return new Row(sha, result.passed(), result.difference(), null, null);
    }

    static List<Row> withJava(List<Row> rows, Map<String, RoundTrip> java, Coverage coverage) {
        coverage.javaRoundTrip((int) java.values().stream().filter(RoundTrip::passed).count(), java.size());
        return rows.stream().map(row -> java.containsKey(row.blobSha()) ? row.withJava(java.get(row.blobSha())) : row)
                .toList();
    }

    String jsonLines(List<Row> rows) {
        return rows.stream().map(json::writeValueAsString).collect(Collectors.joining("\n"))
                + (rows.isEmpty() ? "" : "\n");
    }
}
