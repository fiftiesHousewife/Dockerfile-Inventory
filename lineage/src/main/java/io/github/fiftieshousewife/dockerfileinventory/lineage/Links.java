package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class Links {

    private final Map<String, Resolved.Node> byDigest;
    private final List<Dockerfile> dockerfiles;
    private final Map<List<Instruction>, List<Dockerfile>> groups;
    private final Map<String, Dockerfile> byBlob;
    private final Set<String> artifactoryImageNames;
    private final StepIndex stepIndex;

    Links(Collection<Resolved.Node> images, List<Dockerfile> dockerfiles, Set<String> artifactoryImageNames) {
        this.byDigest = images.stream().collect(Collectors.toMap(Resolved.Node::configDigest, Function.identity()));
        this.dockerfiles = dockerfiles;
        this.groups = dockerfiles.stream().collect(Collectors.groupingBy(Dockerfile::steps));
        this.byBlob = dockerfiles.stream()
                .collect(Collectors.toMap(Dockerfile::blobSha, Function.identity(), (a, b) -> a));
        this.artifactoryImageNames = artifactoryImageNames;
        this.stepIndex = new StepIndex(dockerfiles);
    }

    static Map<String, Link> of(Collection<Resolved.Node> images, Map<String, Parent> parents,
                                List<Dockerfile> dockerfiles, Set<String> artifactoryImageNames) {
        Links links = new Links(images, dockerfiles, artifactoryImageNames);
        Map<String, Link> linked = new HashMap<>();
        images.forEach(image -> linked.put(image.configDigest(), links.link(image, parents.get(image.configDigest()))));
        return linked;
    }

    Link link(Resolved.Node image, Parent parent) {
        List<Instruction> own = OwnSteps.of(image, Parent.node(parent, byDigest));
        Link link = matched(image, own);
        return parentMissing(parent, link) ? new Link(link.tier(), link.blobShas(), true) : link;
    }

    Link matched(Resolved.Node image, List<Instruction> own) {
        List<Dockerfile> group = groups.get(own);
        return group != null ? exact(group) : weak(own, BasePaths.basePath(image));
    }

    static Link exact(List<Dockerfile> group) {
        Link.Tier tier = group.stream().anyMatch(Dockerfile::released) ? Link.Tier.STRONG : Link.Tier.PROBABLE;
        return new Link(tier, group.stream().map(Dockerfile::blobSha).sorted().toList(), false);
    }

    // Candidates are the Dockerfiles on the image's base and those sharing one of its rare steps; scoring every
    // Dockerfile in the estate against every image would cost too much.
    Link weak(List<Instruction> own, String basePath) {
        Set<Dockerfile> candidates = new LinkedHashSet<>(basePath == null ? List.of()
                : BasePaths.on(basePath, dockerfiles));
        candidates.addAll(stepIndex.candidates(own));
        return Alignment.best(own, List.copyOf(candidates));
    }

    boolean parentMissing(Parent parent, Link link) {
        return (parent == null || parent instanceof Parent.Root) && link.blobShas().stream()
                .map(byBlob::get)
                .anyMatch(d -> BasePaths.heldByArtifactory(d, artifactoryImageNames));
    }
}
