package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Links each image to the Dockerfiles whose steps match its own. Dockerfiles with the same steps form one group
 * (Q17), indexed by those steps, so an exact match is one lookup per image. An image whose source label names a
 * repository the estate holds is linked only among that repository's Dockerfiles (the owner's choice, 7 Nov).
 */
public final class Links {

    static final Link NONE = new Link(Link.Tier.NONE, List.of(), false);
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
        Map<String, List<Dockerfile>> byRepository = new HashMap<>();
        dockerfiles.forEach(d -> d.repositories()
                .forEach(r -> byRepository.computeIfAbsent(r, k -> new ArrayList<>()).add(d)));
        Map<String, Link> links = new HashMap<>();
        for (Resolved.Node image : images) {
            Parent parent = parents.get(image.configDigest());
            List<Instruction> own = OwnSteps.of(image, parentNode(parent, byDigest));
            Link link = SourceLabels.repository(image.config().labels()).map(byRepository::get)
                    .map(inRepository -> labelled(own, inRepository))
                    .orElseGet(() -> unlabelled(own, groups.get(own), basePath(image), dockerfiles));
            boolean missing = (parent == null || parent instanceof Parent.Root) && link.blobShas().stream()
                    .map(byBlob::get)
                    .anyMatch(d -> heldByArtifactory(d, artifactoryImageNames));
            links.put(image.configDigest(), missing ? new Link(link.tier(), link.blobShas(), true) : link);
        }
        return links;
    }

    private static Link unlabelled(List<Instruction> own, List<Dockerfile> group, String basePath,
            List<Dockerfile> dockerfiles) {
        return group != null ? exact(group) : weak(own, basePath, dockerfiles);
    }

    /**
     * Among the Dockerfiles of the repository the image's label names: those with the image's steps, else those that
     * align best, whatever base they name (the label already says where the image came from, and a release pipeline
     * often swaps the base). None aligning is no link: the label rules out other repositories.
     */
    private static Link labelled(List<Instruction> own, List<Dockerfile> inRepository) {
        List<Dockerfile> same = inRepository.stream().filter(d -> d.steps().equals(own)).toList();
        return same.isEmpty() ? bestAligned(own, inRepository) : exact(same);
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
        return bestAligned(own, dockerfiles.stream()
                .filter(d -> d.fromPath() != null && samePath(d.fromPath(), basePath)).toList());
    }

    /** The candidates tied for the best alignment with the image's steps, when it reaches {@link #MIN_ALIGNMENT}. */
    private static Link bestAligned(List<Instruction> own, List<Dockerfile> candidates) {
        Map<List<Instruction>, Double> scores = new HashMap<>();
        Map<String, Double> byBlob = new HashMap<>();
        candidates.forEach(d -> byBlob.put(d.blobSha(), scores.computeIfAbsent(d.steps(), s -> alignment(own, s))));
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

    /**
     * How well an image's steps match a Dockerfile's, as a share of the longer. The steps other than {@code LABEL} are
     * aligned in order (their longest common subsequence); the labels count as one more step, matched when every key
     * the Dockerfile sets is among the image's, wherever either sets them (the owner's choice, 7 Nov): build tooling
     * moves, merges and adds labels. A Dockerfile without labels adds no label step, as tooling labels any image.
     */
    static double alignment(List<Instruction> image, List<Instruction> dockerfile) {
        List<Instruction> imageSteps = withoutLabels(image);
        List<Instruction> dockerfileSteps = withoutLabels(dockerfile);
        Set<String> dockerfileKeys = labelKeys(dockerfile);
        int labelStep = dockerfileKeys.isEmpty() ? 0 : 1;
        int matched = commonSubsequence(imageSteps, dockerfileSteps)
                + (labelStep == 1 && labelKeys(image).containsAll(dockerfileKeys) ? 1 : 0);
        int longer = Math.max(imageSteps.size(), dockerfileSteps.size()) + labelStep;
        return longer == 0 ? 0 : (double) matched / longer;
    }

    private static int commonSubsequence(List<Instruction> a, List<Instruction> b) {
        int[][] lcs = new int[a.size() + 1][b.size() + 1];
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                lcs[i][j] = a.get(i - 1).equals(b.get(j - 1))
                        ? lcs[i - 1][j - 1] + 1
                        : Math.max(lcs[i - 1][j], lcs[i][j - 1]);
            }
        }
        return lcs[a.size()][b.size()];
    }

    private static List<Instruction> withoutLabels(List<Instruction> steps) {
        return steps.stream().filter(step -> !step.keyword().equals("LABEL")).toList();
    }

    private static Set<String> labelKeys(List<Instruction> steps) {
        return steps.stream().filter(step -> step.keyword().equals("LABEL"))
                .flatMap(step -> Arrays.stream(step.text().split(" "))).filter(key -> !key.isEmpty())
                .collect(Collectors.toSet());
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
    static Resolved.Node parentNode(Parent parent, Map<String, Resolved.Node> byDigest) {
        return switch (parent) {
            case Parent.Of of -> byDigest.get(of.configDigest());
            case Parent.Ambiguous ambiguous -> byDigest.get(ambiguous.configDigests().getFirst());
            case Parent.Root root -> null;
            case null -> null;
        };
    }
}
