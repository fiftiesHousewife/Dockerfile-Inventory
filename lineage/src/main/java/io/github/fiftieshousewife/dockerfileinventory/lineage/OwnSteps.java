package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

/**
 * The steps an image's own Dockerfile made, as opposed to those it inherited. With a parent in the tree, they are
 * the history after the parent's; without one, they start at the last step Buildah marked {@code FROM <base>}; with
 * neither, the whole history is the image's own. Steps a build service added are dropped (Q13).
 */
public final class OwnSteps {

    private OwnSteps() {
    }

    public static List<Instruction> of(Resolved.Node image, Resolved.Node parent) {
        List<NormalStep> steps = image.config().history().stream().map(HistoryNormaliser::normalise).toList();
        int start = parent != null ? parent.config().history().size() : lastFrom(steps);
        return steps.subList(Math.min(start, steps.size()), steps.size()).stream()
                .filter(step -> !step.builderAdded())
                .map(step -> new Instruction(step.keyword(), step.text()))
                .toList();
    }

    private static int lastFrom(List<NormalStep> steps) {
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).from() != null) {
                return i;
            }
        }
        return 0;
    }
}
