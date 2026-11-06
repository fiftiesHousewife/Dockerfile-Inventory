package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GapsTest {

    private static final Link LINKED = new Link(Link.Tier.WEAK, List.of("df"), false);

    @Test
    void aNonRootImageWithNoLinkIsAGapWithItsReferencesAndLatestPush() {
        List<Reference> refs = List.of(ref("docker-local", "team/app", "1.0", "2026-10-01T00:00:00Z"),
                ref("docker-local", "team/app", "1.1", "2026-10-05T00:00:00Z"));

        GapReport report = Gaps.of(Map.of("app", refs, "base", List.of()),
                Map.of("app", new Parent.Of("base"), "base", new Parent.Root()),
                Map.of("app", Links.NONE, "base", LINKED));

        assertThat(report.gaps()).containsExactly(new GapRow("app", refs, Instant.parse("2026-10-05T00:00:00Z")));
    }

    static Reference ref(String repository, String image, String tag, String pushed) {
        return new Reference(repository, image, tag, Instant.parse(pushed));
    }
}
