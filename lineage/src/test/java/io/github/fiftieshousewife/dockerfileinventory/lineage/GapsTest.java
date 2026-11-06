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

    @Test
    void aRootWithNoLinkIsAnExternalBaseAndALinkedImageIsNeither() {
        List<Reference> ubi = List.of(ref("base-images", "ubi9/ubi-minimal", "9.4", "2026-09-01T00:00:00Z"));

        GapReport report = Gaps.of(
                Map.of("ubi", ubi, "app", List.of(), "squashed", List.of()),
                Map.of("ubi", new Parent.Root(), "app", new Parent.Of("ubi"), "squashed", new Parent.Root()),
                Map.of("ubi", Links.NONE, "app", LINKED, "squashed", LINKED));

        assertThat(report.externalBases())
                .containsExactly(new GapRow("ubi", ubi, Instant.parse("2026-09-01T00:00:00Z")));
        assertThat(report.gaps()).isEmpty();
    }

    @Test
    void anImageWhoseParentIsNotTheNewestPushOfItsImageNameIsOutdated() {
        GapReport report = Gaps.of(
                Map.of("base-old", List.of(ref("docker-local", "team/base", "1", "2026-09-01T00:00:00Z")),
                        "base-new", List.of(ref("docker-local", "team/base", "2", "2026-10-01T00:00:00Z")),
                        "app", List.of(ref("docker-local", "team/app", "1.0", "2026-10-02T00:00:00Z"))),
                Map.of("base-old", new Parent.Root(), "base-new", new Parent.Root(), "app", new Parent.Of("base-old")),
                Map.of("base-old", LINKED, "base-new", LINKED, "app", LINKED));

        assertThat(report.outdatedBases()).containsExactly(new Outdated("app", "base-old", "team/base", "base-new"));
    }

    @Test
    void theSameImageNameInTwoRepositoriesIsOneName() {
        GapReport report = Gaps.of(
                Map.of("base-old", List.of(ref("docker-dev", "team/base", "1", "2026-09-01T00:00:00Z")),
                        "base-new", List.of(ref("docker-prod", "team/base", "2", "2026-10-01T00:00:00Z")),
                        "app", List.of()),
                Map.of("base-old", new Parent.Root(), "base-new", new Parent.Root(), "app", new Parent.Of("base-old")),
                Map.of("base-old", LINKED, "base-new", LINKED, "app", LINKED));

        assertThat(report.outdatedBases()).containsExactly(new Outdated("app", "base-old", "team/base", "base-new"));
    }

    @Test
    void aNewestParentAnAmbiguousParentWithANewestCandidateAndAnUndatedParentAreNotOutdated() {
        GapReport report = Gaps.of(
                Map.of("twin-a", List.of(ref("docker-local", "team/base", "1", "2026-09-01T00:00:00Z")),
                        "twin-b", List.of(ref("docker-local", "team/base", "1b", "2026-10-01T00:00:00Z")),
                        "undated", List.of(new Reference("docker-local", "team/other", "1", null)),
                        "on-newest", List.of(), "on-twins", List.of(), "on-undated", List.of()),
                Map.of("twin-a", new Parent.Root(), "twin-b", new Parent.Root(), "undated", new Parent.Root(),
                        "on-newest", new Parent.Of("twin-b"),
                        "on-twins", new Parent.Ambiguous(List.of("twin-a", "twin-b")),
                        "on-undated", new Parent.Of("undated")),
                Map.of("twin-a", LINKED, "twin-b", LINKED, "undated", LINKED,
                        "on-newest", LINKED, "on-twins", LINKED, "on-undated", LINKED));

        assertThat(report.outdatedBases()).isEmpty();
    }

    static Reference ref(String repository, String image, String tag, String pushed) {
        return new Reference(repository, image, tag, Instant.parse(pushed));
    }
}
