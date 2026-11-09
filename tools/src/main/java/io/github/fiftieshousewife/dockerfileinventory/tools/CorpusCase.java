package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.databind.ObjectMapper;

// A case's expected.json: where the file came from, why it is in the bank, and what the pipeline made of it.
final class CorpusCase {

    private static final ObjectMapper JSON = new ObjectMapper();
    // As jq prints, which the bank's earlier cases were written with.
    private static final DefaultPrettyPrinter AS_JQ = new DefaultPrettyPrinter(Separators.createDefaultInstance()
            .withObjectNameValueSpacing(Separators.Spacing.AFTER));

    private CorpusCase() {
    }

    static String expected(RunOutcomes.Outcome outcome, String why, RunOutcomes outcomes) {
        RunOutcomes.Location location = outcomes.locations().get(outcome.blobSha());
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("repository", location == null ? "" : location.repository());
        source.put("path", location == null ? "" : location.path());
        source.put("commit", location == null ? "" : location.commit());
        source.put("blobSha", outcome.blobSha());
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("source", source);
        expected.put("why", why);
        expected.put("parse", outcome.parse());
        expected.put("unreadableRuns", outcome.unreadableRuns());
        expected.put("roundTrip", outcomes.roundTrips().getOrDefault(outcome.blobSha(), "not-parsed"));
        return JSON.writer().with(AS_JQ).writeValueAsString(expected) + "\n";
    }
}
