package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class OwnSteps {

    private static final String ART_REPO_SWAP = "rm -f /etc/yum.repos.d/art-* ";
    private static final String ART_CLEANUP = "rm -rf /tmp/art";
    private static final NormalStep ROOT = new NormalStep("USER", "0", null, false);

    private OwnSteps() {
    }

    // ARG steps are left out, as facts leaves them out of a Dockerfile's.
    static List<Instruction> of(Resolved.Node image, Resolved.Node parent) {
        List<NormalStep> steps = image.config().history().stream().map(HistoryNormaliser::normalise).toList();
        int start = parent != null ? parent.config().history().size() : lastFrom(steps);
        return withoutReleaseTooling(steps.subList(Math.min(start, steps.size()), steps.size())).stream()
                .filter(step -> !step.builderAdded() && !step.keyword().equals("ARG"))
                .map(step -> new Instruction(step.keyword(), step.text()))
                .toList();
    }

    // Red Hat's release tooling (ART's doozer) wraps a Dockerfile's steps in its own: before them, build settings,
    // a /tmp/art of yum repositories and certificates, ending with ENV __doozer=merge; after them, USER 0, the
    // repositories swapped back and rm -rf /tmp/art. Only their order marks them (a Dockerfile can hold a USER 0 or
    // a yum COPY too), so they are cut by position.
    static List<NormalStep> withoutReleaseTooling(List<NormalStep> steps) {
        int merge = lastIndex(steps, OwnSteps::doozerEnv);
        if (merge < 0) {
            return steps;
        }
        int swap = IntStream.range(merge + 1, steps.size()).filter(i -> runStarts(steps.get(i), ART_REPO_SWAP))
                .findFirst().orElse(steps.size());
        int end = swap > merge + 1 && steps.get(swap - 1).equals(ROOT) ? swap - 1 : swap;
        int after = swap + 1 < steps.size() && runStarts(steps.get(swap + 1), ART_CLEANUP) ? swap + 2 : swap + 1;
        return Stream.concat(steps.subList(merge + 1, end).stream(),
                steps.subList(Math.min(after, steps.size()), steps.size()).stream()).toList();
    }

    static boolean doozerEnv(NormalStep step) {
        return step.keyword().equals("ENV") && List.of(step.text().split(" ")).contains("__doozer");
    }

    static boolean runStarts(NormalStep step, String prefix) {
        return step.keyword().equals("RUN") && step.text().startsWith(prefix);
    }

    static int lastIndex(List<NormalStep> steps, Predicate<NormalStep> test) {
        return IntStream.iterate(steps.size() - 1, i -> i >= 0, i -> i - 1).filter(i -> test.test(steps.get(i)))
                .findFirst().orElse(-1);
    }

    static int lastFrom(List<NormalStep> steps) {
        return Math.max(0, lastIndex(steps, step -> step.from() != null));
    }
}
