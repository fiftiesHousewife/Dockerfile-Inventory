package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collects the real Dockerfiles a run found hard into the fixture bank, each with the outcome the pipeline gave it:
 * every parse failure that is not a template, a few templates, every file with a {@code RUN} the shell reader cannot
 * follow, every file that parses but fails the round trip, and a few per kind of parser workaround. An existing case
 * is kept as it is; a file with a secret-shaped value is never copied. {@code collectCorpus --args=<run directory>};
 * settings {@code PER_KIND} (4), {@code TEMPLATES} (5), {@code CORPUS} ({@code testdata/corpus/dockerfiles}).
 */
public final class CorpusCollect {

    private static final Pattern SECRET_SHAPE = Pattern.compile(
            "AKIA[0-9A-Z]{16}|-----BEGIN [A-Z ]*PRIVATE KEY-----|eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.");

    private final Path run;
    private final Path corpus;
    private final int perKind;
    private final int templates;

    CorpusCollect(Path run, Path corpus, int perKind, int templates) {
        this.run = run;
        this.corpus = corpus;
        this.perKind = perKind;
        this.templates = templates;
    }

    /**
     * Collects from the run named by the one argument into {@code CORPUS}.
     *
     * @param args the run directory
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: collectCorpus --args=<run directory>, e.g. --args=out/all");
            System.exit(2);
        }
        System.out.println("corpus: " + of(Path.of(args[0]), System.getenv()).collect() + " cases added");
    }

    static CorpusCollect of(Path run, Map<String, String> env) {
        return new CorpusCollect(run, Path.of(env.getOrDefault("CORPUS", "testdata/corpus/dockerfiles")),
                Integer.parseInt(env.getOrDefault("PER_KIND", "4")),
                Integer.parseInt(env.getOrDefault("TEMPLATES", "5")));
    }

    int collect() {
        RunOutcomes outcomes = RunOutcomes.read(run);
        return failed(outcomes) + templates(outcomes) + unreadableRuns(outcomes) + roundTrips(outcomes)
                + retried(outcomes);
    }

    int failed(RunOutcomes outcomes) {
        return writeAll(outcomes.failures().stream().filter(o -> o.parse().equals("failed")), "failed",
                "rewrite-docker rejects it", outcomes);
    }

    int templates(RunOutcomes outcomes) {
        return writeAll(outcomes.failures().stream().filter(o -> o.parse().equals("template")).limit(templates),
                "template", "a template saved under a Dockerfile's name", outcomes);
    }

    int unreadableRuns(RunOutcomes outcomes) {
        return writeAll(outcomes.parsed().stream().filter(o -> o.unreadableRuns() > 0), "unreadable-run",
                "a RUN the shell reader cannot follow", outcomes);
    }

    int roundTrips(RunOutcomes outcomes) {
        return writeAll(outcomes.byBlob().stream().filter(o -> "fails".equals(outcomes.roundTrips().get(o.blobSha()))),
                "roundtrip", "parses but does not round-trip", outcomes);
    }

    private int writeAll(Stream<RunOutcomes.Outcome> cases, String prefix, String why, RunOutcomes outcomes) {
        return cases.mapToInt(outcome -> write(prefix, outcome, why, outcomes)).sum();
    }

    int retried(RunOutcomes outcomes) {
        Map<String, List<RunOutcomes.Outcome>> byKind = outcomes.parsed().stream()
                .filter(o -> o.parse().equals("retried") && !secretShaped(o.blobSha(), outcomes))
                .collect(Collectors.groupingBy(o -> Workaround.of(text(o.blobSha())), LinkedHashMap::new,
                        Collectors.toList()));
        return byKind.entrySet().stream().mapToInt(kind -> writeAll(kind.getValue().stream().limit(perKind),
                "retried-" + kind.getKey(), "parses only with the parser's " + kind.getKey() + " workaround",
                outcomes)).sum();
    }

    int write(String prefix, RunOutcomes.Outcome outcome, String why, RunOutcomes outcomes) {
        Path dir = corpus.resolve(prefix + "-" + outcome.blobSha().substring(0, 8));
        if (Files.isDirectory(dir) || secretShaped(outcome.blobSha(), outcomes)) {
            return 0;
        }
        try {
            Files.createDirectories(dir);
            Files.copy(run.resolve("discovery/blobs").resolve(outcome.blobSha()), dir.resolve("Dockerfile"));
            Files.writeString(dir.resolve("expected.json"), CorpusCase.expected(outcome, why, outcomes));
            System.out.println("added " + dir.getFileName());
            return 1;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    boolean secretShaped(String blobSha, RunOutcomes outcomes) {
        return outcomes.secrets().contains(blobSha) || SECRET_SHAPE.matcher(text(blobSha)).find();
    }

    private String text(String blobSha) {
        try {
            return Files.readString(run.resolve("discovery/blobs").resolve(blobSha));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<String> inputs() {
        return List.of("discovery/manifest.jsonl", "facts/facts.jsonl", "facts/parse-failures.jsonl",
                "converter/roundtrip.jsonl", "rules/findings.jsonl");
    }
}
