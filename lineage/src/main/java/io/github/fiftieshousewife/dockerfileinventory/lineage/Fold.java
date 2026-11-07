package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class Fold {

    record Member(Resolved.Node image, List<Instruction> own) {
    }

    record External(String path, List<Instruction> steps) {
    }

    private Fold() {
    }

    static Map<String, EffectiveImage> of(Collection<Resolved.Node> images, Map<String, Parent> parents,
                                          Map<String, Link> links) {
        Map<String, Resolved.Node> byDigest = images.stream()
                .collect(Collectors.toMap(Resolved.Node::configDigest, Function.identity()));
        Map<String, EffectiveImage> effective = new HashMap<>();
        images.forEach(image -> effective.put(image.configDigest(),
                effective(image, chain(image, parents, byDigest), links)));
        return effective;
    }

    // The values come from the image's own config, which the builder has already folded; the chain says which
    // image set each one.
    static EffectiveImage effective(Resolved.Node image, List<Member> chain, Map<String, Link> links) {
        Attribution by = new Attribution(chain, external(chain.getLast().image()), links);
        ImageConfig config = image.config();
        return new EffectiveImage(
                chain.stream().map(m -> new ChainEntry(m.image().configDigest(), by.link(m))).toList(),
                new Property<>(config.user(), by.setBy(keyword("USER"))),
                new Property<>(config.workingDir(), by.setBy(keyword("WORKDIR"))),
                new Property<>(config.entrypoint(), by.setBy(keyword("ENTRYPOINT"))),
                new Property<>(config.cmd(), by.setBy(keyword("CMD"))),
                by.perKey(config.env(), "ENV"),
                by.perKey(config.labels(), "LABEL"),
                chain.size() > 1 && !chain.get(1).image().config().onBuild().isEmpty());
    }

    static List<Member> chain(Resolved.Node image, Map<String, Parent> parents, Map<String, Resolved.Node> byDigest) {
        List<Member> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Resolved.Node current = image;
        while (current != null && seen.add(current.configDigest())) {
            Resolved.Node parent = Parent.node(parents.get(current.configDigest()), byDigest);
            chain.add(new Member(current, OwnSteps.of(current, parent)));
            current = parent;
        }
        return chain;
    }

    private static Predicate<Instruction> keyword(String keyword) {
        return instruction -> instruction.keyword().equals(keyword);
    }

    // Buildah marks a build's first step with a FROM comment, so the steps before a root's last one came from the
    // base it names.
    static External external(Resolved.Node root) {
        List<NormalStep> steps = root.config().history().stream().map(HistoryNormaliser::normalise).toList();
        return IntStream.iterate(steps.size() - 1, i -> i >= 0, i -> i - 1)
                .filter(i -> steps.get(i).from() != null)
                .mapToObj(i -> new External(ImagePaths.path(steps.get(i).from()), steps.subList(0, i).stream()
                        .map(step -> new Instruction(step.keyword(), step.text()))
                        .toList()))
                .findFirst()
                .orElse(null);
    }

    record Attribution(List<Member> chain, External external, Map<String, Link> links) {

        Link link(Member member) {
            return links.get(member.image().configDigest());
        }

        SetBy setBy(Predicate<Instruction> sets) {
            return chain.stream()
                    .filter(member -> member.own().stream().anyMatch(sets))
                    .findFirst()
                    .<SetBy>map(member -> new SetBy.Image(member.image().configDigest(), link(member)))
                    .orElseGet(() -> external != null && external.steps().stream().anyMatch(sets)
                            ? new SetBy.ExternalBase(external.path())
                            : new SetBy.Unknown());
        }

        // ENV and LABEL steps reduce to their key names, so each key is attributed on its own.
        Map<String, Property<String>> perKey(Map<String, String> values, String keyword) {
            Map<String, Property<String>> attributed = new HashMap<>();
            values.forEach((key, value) -> attributed.put(key, new Property<>(value,
                    setBy(keyword(keyword).and(i -> List.of(i.text().split(" ")).contains(key))))));
            return attributed;
        }
    }
}
