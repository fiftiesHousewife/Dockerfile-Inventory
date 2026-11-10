package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

record LineageRates(int tags, int images, Map<Resolved.Reason, Integer> skipped, double lineageCoverage,
                    int ambiguousParents, Map<Link.Tier, Integer> links, int credited, int parentMissing,
                    int onbuildChildren, int gaps, int externalBases, int outdatedBases) {

    LineageRates {
        skipped = Collections.unmodifiableMap(new TreeMap<>(skipped));
        links = Collections.unmodifiableMap(new TreeMap<>(links));
    }

    static LineageRates of(List<TagRow> tags, Collection<Parent> parents, Collection<Link> links,
                           Collection<EffectiveImage> effective, GapReport gaps) {
        Map<Resolved.Reason, Integer> skipped = skipped(tags);
        return new LineageRates(tags.size(), parents.size(), skipped, coverage(tags, skipped),
                (int) parents.stream().filter(p -> p instanceof Parent.Ambiguous).count(),
                tiers(links),
                (int) links.stream().filter(Link::credited).count(),
                (int) links.stream().filter(Link::parentMissing).count(),
                (int) effective.stream().filter(EffectiveImage::parentHasOnbuild).count(),
                gaps.gaps().size(), gaps.externalBases().size(), gaps.outdatedBases().size());
    }

    static Map<Resolved.Reason, Integer> skipped(List<TagRow> tags) {
        Map<Resolved.Reason, Integer> skipped = new EnumMap<>(Resolved.Reason.class);
        tags.stream().filter(t -> t.skipped() != null).forEach(t -> skipped.merge(t.skipped(), 1, Integer::sum));
        return skipped;
    }

    static double coverage(List<TagRow> tags, Map<Resolved.Reason, Integer> skipped) {
        long placed = tags.stream().filter(t -> t.configDigest() != null).count();
        long inScope = tags.size() - skipped.getOrDefault(Resolved.Reason.NO_AMD64, 0)
                - skipped.getOrDefault(Resolved.Reason.GONE, 0);
        return inScope == 0 ? 0 : (double) placed / inScope;
    }

    static Map<Link.Tier, Integer> tiers(Collection<Link> links) {
        Map<Link.Tier, Integer> tiers = new EnumMap<>(Link.Tier.class);
        links.forEach(l -> tiers.merge(l.tier(), 1, Integer::sum));
        return tiers;
    }
}
