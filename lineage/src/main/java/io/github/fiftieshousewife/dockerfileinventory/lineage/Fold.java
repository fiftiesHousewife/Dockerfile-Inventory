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

    private Fold() {
    }

    public static Map<String, EffectiveImage> of(Collection<Resolved.Node> images, Map<String, Parent> parents,
                                                 Map<String, Link> links) {
        Map<String, Resolved.Node> byDigest = images.stream()
                .collect(Collectors.toMap(Resolved.Node::configDigest, Function.identity()));
        Map<String, EffectiveImage> effective = new HashMap<>();
        for (Resolved.Node image : images) {
            List<Member> chain = chain(image, parents, byDigest);
            ImageConfig config = image.config();
            effective.put(image.configDigest(), new EffectiveImage(
                    new Property<>(config.user(), setBy(chain, links, keyword("USER"))),
                    new Property<>(config.workingDir(), setBy(chain, links, keyword("WORKDIR"))),
                    new Property<>(config.entrypoint(), setBy(chain, links, keyword("ENTRYPOINT"))),
                    new Property<>(config.cmd(), setBy(chain, links, keyword("CMD"))),
                    perKey(config.env(), "ENV", chain, links),
                    perKey(config.labels(), "LABEL", chain, links)));
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
                                                        List<Member> chain, Map<String, Link> links) {
        Map<String, Property<String>> attributed = new HashMap<>();
        values.forEach((key, value) -> attributed.put(key, new Property<>(value, setBy(chain, links,
                keyword(keyword).and(i -> List.of(i.text().split(" ")).contains(key))))));
        return attributed;
    }

    private static Predicate<Instruction> keyword(String keyword) {
        return instruction -> instruction.keyword().equals(keyword);
    }

    /** The nearest image in the chain whose own steps include one the predicate accepts. */
    private static SetBy setBy(List<Member> chain, Map<String, Link> links, Predicate<Instruction> sets) {
        return chain.stream()
                .filter(member -> member.own().stream().anyMatch(sets))
                .findFirst()
                .<SetBy>map(member -> new SetBy.Image(member.image().configDigest(),
                        links.get(member.image().configDigest())))
                .orElseGet(SetBy.Unknown::new);
    }
}
