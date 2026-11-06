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

/**
 * Folds each image's chain into its effective properties. The values come from the image's own config, which the
 * builder has already folded; what this adds is which image in the chain set each one, so a rule about an inherited
 * property can name the Dockerfile to change.
 */
public final class Fold {

    /** One image in a chain, with the steps its own Dockerfile made. */
    private record Member(Resolved.Node image, List<Instruction> own) {
    }

    /** The base a root names in its {@code FROM} comment, outside Artifactory, with the steps it inherited from it. */
    private record External(String path, List<Instruction> steps) {
    }

    private Fold() {
    }

    public static Map<String, EffectiveImage> of(Collection<Resolved.Node> images, Map<String, Parent> parents,
                                                 Map<String, Link> links) {
        Map<String, Resolved.Node> byDigest = images.stream()
                .collect(Collectors.toMap(Resolved.Node::configDigest, Function.identity()));
        Map<String, EffectiveImage> effective = new HashMap<>();
        for (Resolved.Node image : images) {
            List<Member> chain = chain(image, parents, byDigest);
            External external = external(chain.getLast().image());
            ImageConfig config = image.config();
            effective.put(image.configDigest(), new EffectiveImage(
                    new Property<>(config.user(), setBy(chain, external, links, keyword("USER"))),
                    new Property<>(config.workingDir(), setBy(chain, external, links, keyword("WORKDIR"))),
                    new Property<>(config.entrypoint(), setBy(chain, external, links, keyword("ENTRYPOINT"))),
                    new Property<>(config.cmd(), setBy(chain, external, links, keyword("CMD"))),
                    perKey(config.env(), "ENV", chain, external, links),
                    perKey(config.labels(), "LABEL", chain, external, links)));
        }
        return effective;
    }

    /** The image, then each ancestor to the root; a repeated image ends the walk. */
    private static List<Member> chain(Resolved.Node image, Map<String, Parent> parents,
                                      Map<String, Resolved.Node> byDigest) {
        List<Member> chain = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Resolved.Node current = image;
        while (current != null && seen.add(current.configDigest())) {
            Resolved.Node parent = Links.parentNode(parents.get(current.configDigest()), byDigest);
            chain.add(new Member(current, OwnSteps.of(current, parent)));
            current = parent;
        }
        return chain;
    }

    /** {@code ENV} and {@code LABEL} steps reduce to their key names, so each key is attributed on its own. */
    private static Map<String, Property<String>> perKey(Map<String, String> values, String keyword,
                                                        List<Member> chain, External external,
                                                        Map<String, Link> links) {
        Map<String, Property<String>> attributed = new HashMap<>();
        values.forEach((key, value) -> attributed.put(key, new Property<>(value, setBy(chain, external, links,
                keyword(keyword).and(i -> List.of(i.text().split(" ")).contains(key))))));
        return attributed;
    }

    private static Predicate<Instruction> keyword(String keyword) {
        return instruction -> instruction.keyword().equals(keyword);
    }

    /**
     * The nearest image in the chain whose own steps include one the predicate accepts; failing that, the external
     * base the root names, if the steps the root inherited from it include one.
     */
    private static SetBy setBy(List<Member> chain, External external, Map<String, Link> links,
                               Predicate<Instruction> sets) {
        return chain.stream()
                .filter(member -> member.own().stream().anyMatch(sets))
                .findFirst()
                .<SetBy>map(member -> new SetBy.Image(member.image().configDigest(),
                        links.get(member.image().configDigest())))
                .orElseGet(() -> external != null && external.steps().stream().anyMatch(sets)
                        ? new SetBy.ExternalBase(external.path())
                        : new SetBy.Unknown());
    }

    /** The steps before a root's last {@code FROM} comment came from the base that comment names. */
    private static External external(Resolved.Node root) {
        List<NormalStep> steps = root.config().history().stream().map(HistoryNormaliser::normalise).toList();
        for (int i = steps.size() - 1; i >= 0; i--) {
            String from = steps.get(i).from();
            if (from != null) {
                return new External(ImagePaths.path(from), steps.subList(0, i).stream()
                        .map(step -> new Instruction(step.keyword(), step.text()))
                        .toList());
            }
        }
        return null;
    }
}
