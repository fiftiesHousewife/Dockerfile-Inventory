package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.util.LinkedHashMap;
import java.util.Map;

final class CorpusCase {

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
        return Json.pretty(expected);
    }
}
