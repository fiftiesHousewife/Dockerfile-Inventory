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
                Map.of("app", Link.NONE, "base", LINKED));

        assertThat(report.gaps()).containsExactly(new GapRow("app", refs, Instant.parse("2026-10-05T00:00:00Z")));
    }

    @Test
    void aRootWithNoLinkIsAnExternalBaseAndALinkedImageIsNeither() {
        List<Reference> ubi = List.of(ref("base-images", "ubi9/ubi-minimal", "9.4", "2026-09-01T00:00:00Z"));

        GapReport report = Gaps.of(
                Map.of("ubi", ubi, "app", List.of(), "squashed", List.of()),
                Map.of("ubi", new Parent.Root(), "app", new Parent.Of("ubi"), "squashed", new Parent.Root()),
                Map.of("ubi", Link.NONE, "app", LINKED, "squashed", LINKED));

        assertThat(report).satisfies(r -> {
            assertThat(r.externalBases())
                    .containsExactly(new GapRow("ubi", ubi, Instant.parse("2026-09-01T00:00:00Z")));
            assertThat(r.gaps()).isEmpty();
        });
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

    @Test
    void anImagesOutdatedCandidatesComeFromItsParent() {
        assertThat(Gaps.candidates(new Parent.Of("base"))).containsExactly("base");
        assertThat(Gaps.candidates(new Parent.Ambiguous(List.of("a", "b")))).containsExactly("a", "b");
        assertThat(Gaps.candidates(new Parent.Root())).isEmpty();
    }

    @Test
    void theNewestDigestOfEachImageNameIgnoresUndatedPushes() {
        assertThat(Gaps.newestByName(Map.of(
                "old", List.of(ref("docker-local", "team/base", "1", "2026-09-01T00:00:00Z")),
                "new", List.of(ref("docker-local", "team/base", "2", "2026-10-01T00:00:00Z")),
                "undated", List.of(new Reference("docker-local", "team/base", "3", null)))))
                .isEqualTo(Map.of("team/base", "new"));
    }

    @Test
    void aGapRowWithNoDatedPushHasNoLatestPush() {
        List<Reference> undated = List.of(new Reference("docker-local", "team/app", "1", null));

        assertThat(Gaps.row("app", undated)).isEqualTo(new GapRow("app", undated, null));
    }

    @Test
    void unlinkedSplitsRootsFromBuiltImages() {
        Map<String, List<Reference>> references = Map.of("root", List.of(), "built", List.of());
        Map<String, Parent> parents = Map.of("root", new Parent.Root(), "built", new Parent.Of("root"));
        Map<String, Link> links = Map.of("root", Link.NONE, "built", Link.NONE);

        assertThat(Gaps.unlinked(references, parents, links, true)).extracting(GapRow::configDigest)
                .containsExactly("root");
        assertThat(Gaps.unlinked(references, parents, links, false)).extracting(GapRow::configDigest)
                .containsExactly("built");
    }

    @Test
    void anImageIsOutdatedOncePerImageNameItsParentWasPushedUnder() {
        Map<String, List<Reference>> references = Map.of("old", List.of(
                ref("docker-local", "team/base", "1", "2026-09-01T00:00:00Z"),
                ref("docker-local", "team/alias", "1", "2026-09-01T00:00:00Z")));

        assertThat(Gaps.outdated("app", List.of("old"), references,
                Map.of("team/base", "new", "team/alias", "newer")))
                .containsExactly(new Outdated("app", "old", "team/alias", "newer"),
                        new Outdated("app", "old", "team/base", "new"));
    }

    static Reference ref(String repository, String image, String tag, String pushed) {
        return new Reference(repository, image, tag, Instant.parse(pushed));
    }
}
