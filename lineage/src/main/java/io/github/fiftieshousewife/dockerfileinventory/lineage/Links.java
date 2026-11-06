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
    static final double MIN_ALIGNMENT = 0.8;

    private Links() {
    }

    public static Map<String, Link> of(Collection<Resolved.Node> images, Map<String, Parent> parents,
                                       List<Dockerfile> dockerfiles, Set<String> artifactoryImageNames) {
        Map<String, Resolved.Node> byDigest = images.stream()
                .collect(Collectors.toMap(Resolved.Node::configDigest, Function.identity()));
        Map<List<Instruction>, List<Dockerfile>> groups = dockerfiles.stream()
                .collect(Collectors.groupingBy(Dockerfile::steps));
        Map<String, Dockerfile> byBlob = dockerfiles.stream()
                .collect(Collectors.toMap(Dockerfile::blobSha, Function.identity(), (a, b) -> a));
        Map<String, Link> links = new HashMap<>();
        for (Resolved.Node image : images) {
            Parent parent = parents.get(image.configDigest());
            List<Instruction> own = OwnSteps.of(image, parentNode(parent, byDigest));
            List<Dockerfile> group = groups.get(own);
            Link link = group != null ? exact(group) : weak(own, basePath(image), dockerfiles);
            boolean missing = (parent == null || parent instanceof Parent.Root) && link.blobShas().stream()
                    .map(byBlob::get)
                    .anyMatch(d -> heldByArtifactory(d, artifactoryImageNames));
            links.put(image.configDigest(), missing ? new Link(link.tier(), link.blobShas(), true) : link);
        }
        return links;
    }

    private static Link exact(List<Dockerfile> group) {
        Link.Tier tier = group.stream().anyMatch(Dockerfile::released) ? Link.Tier.STRONG : Link.Tier.PROBABLE;
        return new Link(tier, group.stream().map(Dockerfile::blobSha).sorted().toList(), false);
    }

    /**
     * The Dockerfiles on the image's base whose steps align best with the image's own, when at least
     * {@link #MIN_ALIGNMENT} of the longer side aligns in order. Without a known base there is no weak link: scoring
     * every Dockerfile in the estate against every image would cost too much and mean little.
     */
    private static Link weak(List<Instruction> own, String basePath, List<Dockerfile> dockerfiles) {
        if (basePath == null) {
            return NONE;
        }
        Map<List<Instruction>, Double> scores = new HashMap<>();
        Map<String, Double> byBlob = new HashMap<>();
        for (Dockerfile dockerfile : dockerfiles) {
            if (dockerfile.fromPath() != null && samePath(dockerfile.fromPath(), basePath)) {
                byBlob.put(dockerfile.blobSha(), scores.computeIfAbsent(dockerfile.steps(), s -> alignment(own, s)));
            }
        }
        double best = byBlob.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (best < MIN_ALIGNMENT) {
            return NONE;
        }
        return new Link(Link.Tier.WEAK, byBlob.entrySet().stream()
                .filter(e -> e.getValue() == best)
                .map(Map.Entry::getKey)
                .sorted()
                .toList(), false);
    }

    /** The longest common subsequence of two step lists, as a share of the longer. */
    static double alignment(List<Instruction> a, List<Instruction> b) {
        int[][] lcs = new int[a.size() + 1][b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                lcs[i][j] = a.get(i - 1).equals(b.get(j - 1))
                        ? lcs[i - 1][j - 1] + 1
                        : Math.max(lcs[i - 1][j], lcs[i][j - 1]);
            }
        }
        int longer = Math.max(a.size(), b.size());
        return longer == 0 ? 0 : (double) lcs[a.size()][b.size()] / longer;
    }

    /** Q2: nothing is deleted, so a base Artifactory holds should have been found as the image's parent. */
    private static boolean heldByArtifactory(Dockerfile dockerfile, Set<String> imageNames) {
        String path = dockerfile.fromPath();
        return path != null && (imageNames.contains(path) || imageNames.contains(withoutFirstSegment(path)));
    }

    /** Q15: a Dockerfile may name an Artifactory image with or without the repository key as its first segment. */
    static boolean samePath(String a, String b) {
        return a.equals(b) || withoutFirstSegment(a).equals(b) || a.equals(withoutFirstSegment(b));
    }

    private static String withoutFirstSegment(String path) {
        return path.substring(path.indexOf('/') + 1);
    }

    /** The image path of the base Buildah recorded on the image's own first step, or null when it recorded none. */
    private static String basePath(Resolved.Node image) {
        List<HistoryStep> history = image.config().history();
        for (int i = history.size() - 1; i >= 0; i--) {
            String from = HistoryNormaliser.normalise(history.get(i)).from();
            if (from != null) {
                return ImagePaths.path(from);
            }
        }
        return null;
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
