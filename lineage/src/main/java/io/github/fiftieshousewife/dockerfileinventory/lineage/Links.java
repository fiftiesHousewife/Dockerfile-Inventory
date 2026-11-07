package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
    private final Map<String, List<Dockerfile>> byRepository;
    private final Set<String> artifactoryImageNames;

    Links(Collection<Resolved.Node> images, List<Dockerfile> dockerfiles, Set<String> artifactoryImageNames) {
        this.byDigest = images.stream().collect(Collectors.toMap(Resolved.Node::configDigest, Function.identity()));
        this.dockerfiles = dockerfiles;
        this.groups = dockerfiles.stream().collect(Collectors.groupingBy(Dockerfile::steps));
        this.byBlob = dockerfiles.stream()
                .collect(Collectors.toMap(Dockerfile::blobSha, Function.identity(), (a, b) -> a));
        this.byRepository = byRepository(dockerfiles);
        this.artifactoryImageNames = artifactoryImageNames;
    }

    static Map<String, Link> of(Collection<Resolved.Node> images, Map<String, Parent> parents,
                                List<Dockerfile> dockerfiles, Set<String> artifactoryImageNames) {
        Links links = new Links(images, dockerfiles, artifactoryImageNames);
        Map<String, Link> linked = new HashMap<>();
        images.forEach(image -> linked.put(image.configDigest(), links.link(image, parents.get(image.configDigest()))));
        return linked;
    }

    static Map<String, List<Dockerfile>> byRepository(List<Dockerfile> dockerfiles) {
        Map<String, List<Dockerfile>> byRepository = new HashMap<>();
        dockerfiles.forEach(d -> d.repositories()
                .forEach(r -> byRepository.computeIfAbsent(r, k -> new ArrayList<>()).add(d)));
        return byRepository;
    }

    Link link(Resolved.Node image, Parent parent) {
        List<Instruction> own = OwnSteps.of(image, Parent.node(parent, byDigest));
        Link link = matched(image, own);
        return parentMissing(parent, link) ? new Link(link.tier(), link.blobShas(), true) : link;
    }

    Link matched(Resolved.Node image, List<Instruction> own) {
        return SourceLabels.repository(image.config().labels()).map(byRepository::get)
                .map(inRepository -> labelled(own, inRepository))
                .orElseGet(() -> unlabelled(own, BasePaths.basePath(image)));
    }

    // A source label already says where the image came from, and a release pipeline often swaps the base, so any
    // base will do; but it rules out other repositories, so none aligning is no link.
    static Link labelled(List<Instruction> own, List<Dockerfile> inRepository) {
        List<Dockerfile> same = inRepository.stream().filter(d -> d.steps().equals(own)).toList();
        return same.isEmpty() ? Alignment.best(own, inRepository) : exact(same);
    }

    Link unlabelled(List<Instruction> own, String basePath) {
        List<Dockerfile> group = groups.get(own);
        return group != null ? exact(group) : weak(own, basePath);
    }

    static Link exact(List<Dockerfile> group) {
        Link.Tier tier = group.stream().anyMatch(Dockerfile::released) ? Link.Tier.STRONG : Link.Tier.PROBABLE;
        return new Link(tier, group.stream().map(Dockerfile::blobSha).sorted().toList(), false);
    }

    // Without a known base there is no weak link: scoring every Dockerfile in the estate against every image would
    // cost too much and mean little.
    Link weak(List<Instruction> own, String basePath) {
        return basePath == null ? Link.NONE : Alignment.best(own, BasePaths.on(basePath, dockerfiles));
    }

    boolean parentMissing(Parent parent, Link link) {
        return (parent == null || parent instanceof Parent.Root) && link.blobShas().stream()
                .map(byBlob::get)
                .anyMatch(d -> BasePaths.heldByArtifactory(d, artifactoryImageNames));
    }
}
