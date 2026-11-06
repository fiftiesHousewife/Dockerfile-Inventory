package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Links each image to the Dockerfiles whose steps match its own. Dockerfiles with the same steps form one group
 * (Q17), indexed by those steps, so an exact match is one lookup per image.
 */
public final class Links {

    private static final Link NONE = new Link(Link.Tier.NONE, List.of(), false);

    private Links() {
    }

    public static Map<String, Link> of(Collection<Resolved.Node> images, Map<String, Parent> parents,
                                       List<Dockerfile> dockerfiles, Set<String> artifactoryImageNames) {
        Map<String, Resolved.Node> byDigest = images.stream()
                .collect(Collectors.toMap(Resolved.Node::configDigest, Function.identity()));
        Map<List<Instruction>, List<Dockerfile>> groups = dockerfiles.stream()
                .collect(Collectors.groupingBy(Dockerfile::steps));
        Map<String, Link> links = new HashMap<>();
        for (Resolved.Node image : images) {
            List<Instruction> own = OwnSteps.of(image, parentNode(parents.get(image.configDigest()), byDigest));
            List<Dockerfile> group = groups.get(own);
            links.put(image.configDigest(), group == null ? NONE : exact(group));
        }
        return links;
    }

    private static Link exact(List<Dockerfile> group) {
        Link.Tier tier = group.stream().anyMatch(Dockerfile::released) ? Link.Tier.STRONG : Link.Tier.PROBABLE;
        return new Link(tier, group.stream().map(Dockerfile::blobSha).sorted().toList(), false);
    }

    /** An ambiguous parent's candidates share layers and history, so any of them gives the same own steps. */
    private static Resolved.Node parentNode(Parent parent, Map<String, Resolved.Node> byDigest) {
        return switch (parent) {
            case Parent.Of of -> byDigest.get(of.configDigest());
            case Parent.Ambiguous ambiguous -> byDigest.get(ambiguous.configDigests().getFirst());
            case Parent.Root root -> null;
            case null -> null;
        };
    }
}
