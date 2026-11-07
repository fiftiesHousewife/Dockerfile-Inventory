package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LineageRatesTest {

    private static final List<TagRow> TAGS = List.of(
            tag("c1", null), tag("c1", null), tag(null, Resolved.Reason.NO_AMD64), tag(null, Resolved.Reason.GONE),
            tag(null, Resolved.Reason.UNREADABLE_CONFIG));

    @Test
    void skippedTagsAreCountedByReason() {
        assertThat(LineageRates.skipped(TAGS)).isEqualTo(Map.of(
                Resolved.Reason.NO_AMD64, 1, Resolved.Reason.GONE, 1, Resolved.Reason.UNREADABLE_CONFIG, 1));
    }

    @Test
    void coverageIsThePlacedShareOfTagsLeavingOutThoseWithNoAmd64ImageOrGone() {
        assertThat(LineageRates.coverage(TAGS, LineageRates.skipped(TAGS))).isEqualTo(2.0 / 3);
    }

    @Test
    void coverageWithNoTagInScopeIsZero() {
        List<TagRow> tags = List.of(tag(null, Resolved.Reason.GONE));

        assertThat(LineageRates.coverage(tags, LineageRates.skipped(tags))).isZero();
    }

    @Test
    void linksAreCountedByTier() {
        assertThat(LineageRates.tiers(List.of(Link.NONE, Link.NONE,
                new Link(Link.Tier.WEAK, List.of("df"), false))))
                .isEqualTo(Map.of(Link.Tier.NONE, 2, Link.Tier.WEAK, 1));
    }

    private static TagRow tag(String configDigest, Resolved.Reason skipped) {
        return new TagRow("docker-local", "team/app", "1", null, configDigest, skipped, null);
    }
}
