package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class Alignment {

    static final double MIN = 0.8;

    private Alignment() {
    }

    static Link best(List<Instruction> own, List<Dockerfile> candidates) {
        Map<List<Instruction>, Double> scores = new LinkedHashMap<>();
        candidates.forEach(d -> scores.computeIfAbsent(d.steps(), steps -> score(own, steps)));
        double best = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (best < MIN) {
            return Link.NONE;
        }
        Set<List<Instruction>> winners = scores.entrySet().stream().filter(e -> e.getValue() == best)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        Double runnerUp = winners.size() > 1 ? Double.valueOf(best)
                : scores.values().stream().filter(score -> score < best).max(Double::compare).orElse(null);
        return new Link(Link.Tier.WEAK, candidates.stream().filter(d -> winners.contains(d.steps()))
                .map(Dockerfile::blobSha).distinct().sorted().toList(), false, best, runnerUp);
    }

    // Build tooling moves, merges and adds labels, so the labels count as one step, matched when the image holds
    // every key the Dockerfile sets; a Dockerfile without labels adds no label step, as tooling labels any image.
    static double score(List<Instruction> image, List<Instruction> dockerfile) {
        List<Instruction> imageSteps = withoutLabels(image);
        List<Instruction> dockerfileSteps = withoutLabels(dockerfile);
        Set<String> dockerfileKeys = labelKeys(dockerfile);
        int labelStep = dockerfileKeys.isEmpty() ? 0 : 1;
        int matched = commonSubsequence(imageSteps, dockerfileSteps)
                + (labelStep == 1 && labelKeys(image).containsAll(dockerfileKeys) ? 1 : 0);
        int longer = Math.max(imageSteps.size(), dockerfileSteps.size()) + labelStep;
        return longer == 0 ? 0 : (double) matched / longer;
    }

    static int commonSubsequence(List<Instruction> a, List<Instruction> b) {
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

    static List<Instruction> withoutLabels(List<Instruction> steps) {
        return steps.stream().filter(step -> !step.keyword().equals("LABEL")).toList();
    }

    static Set<String> labelKeys(List<Instruction> steps) {
        return steps.stream().filter(step -> step.keyword().equals("LABEL"))
                .flatMap(step -> Arrays.stream(step.text().split(" "))).filter(key -> !key.isEmpty())
                .collect(Collectors.toSet());
    }
}
