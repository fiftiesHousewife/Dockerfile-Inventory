package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
        Map<String, String> env = System.getenv();
        int written = new CorpusCollect(Path.of(args[0]), Path.of(env.getOrDefault("CORPUS",
                "testdata/corpus/dockerfiles")), Integer.parseInt(env.getOrDefault("PER_KIND", "4")),
                Integer.parseInt(env.getOrDefault("TEMPLATES", "5"))).collect();
        System.out.println("corpus: " + written + " cases added");
    }

    int collect() {
        RunOutcomes outcomes = RunOutcomes.read(run);
        int written = 0;
        for (RunOutcomes.Outcome outcome : outcomes.failures()) {
            if (outcome.parse().equals("failed")) {
                written += write("failed", outcome, "rewrite-docker rejects it", outcomes);
            }
        }
        written += outcomes.failures().stream().filter(o -> o.parse().equals("template")).limit(templates)
                .mapToInt(o -> write("template", o, "a template saved under a Dockerfile's name", outcomes)).sum();
        for (RunOutcomes.Outcome outcome : outcomes.parsed()) {
            if (outcome.unreadableRuns() > 0) {
                written += write("unreadable-run", outcome, "a RUN the shell reader cannot follow", outcomes);
            }
        }
        for (RunOutcomes.Outcome outcome : outcomes.byBlob()) {
            if ("fails".equals(outcomes.roundTrips().get(outcome.blobSha()))) {
                written += write("roundtrip", outcome, "parses but does not round-trip", outcomes);
            }
        }
        return written + retried(outcomes);
    }

    private int retried(RunOutcomes outcomes) {
        Map<String, Integer> kinds = new HashMap<>();
        int written = 0;
        for (RunOutcomes.Outcome outcome : outcomes.parsed()) {
            if (outcome.parse().equals("retried") && !secretShaped(outcome.blobSha(), outcomes)) {
                String kind = Workaround.of(text(outcome.blobSha()));
                if (kinds.merge(kind, 1, Integer::sum) <= perKind) {
                    written += write("retried-" + kind, outcome, "parses only with the parser's " + kind
                            + " workaround", outcomes);
                }
            }
        }
        return written;
    }

    // 1 when the case was written; 0 when it was there already or its file holds a secret's shape.
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
