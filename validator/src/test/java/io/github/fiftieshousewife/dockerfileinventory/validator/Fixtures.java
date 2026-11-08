package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.nio.file.Path;
import java.util.List;

// The real facts lines in fixtures/facts.jsonl, found by the start of their blob SHA.
final class Fixtures {

    static final Path FACTS = Path.of("src/test/resources/fixtures/facts.jsonl");
    private static final List<Subject> SUBJECTS = Subject.read(FACTS);
    static final Profile V2 = Profile.shipped(Profile.DEFAULT);
    static final Profile V1 = Profile.shipped("openshift-4-restricted");

    private Fixtures() {
    }

    static Subject subject(String shaPrefix) {
        return SUBJECTS.stream().filter(s -> s.blobSha().startsWith(shaPrefix)).findFirst().orElseThrow();
    }
}
