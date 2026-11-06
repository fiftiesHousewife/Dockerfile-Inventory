package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LinksTest {

    @Test
    void anImageWhoseOwnStepsEqualAGroupsLinksToEveryDockerfileInIt() {
        Resolved.Node image = image("app", "FROM registry.example.com/team/base:1", "RUN make", "USER 1001");
        List<Dockerfile> dockerfiles = List.of(
                dockerfile("copy-a", "team/base", false, "RUN make", "USER 1001"),
                dockerfile("copy-b", "team/base", false, "RUN make", "USER 1001"),
                dockerfile("other", "team/base", false, "RUN make"));

        assertThat(Links.of(List.of(image), Map.of("app", new Parent.Root()), dockerfiles, Set.of()))
                .containsEntry("app", new Link(Link.Tier.PROBABLE, List.of("copy-a", "copy-b"), false));
    }

    @Test
    void aGroupWithADockerfileAtAReleasedCommitMakesTheLinkStrong() {
        Resolved.Node image = image("app", "FROM registry.example.com/team/base:1", "RUN make");
        List<Dockerfile> dockerfiles = List.of(
                dockerfile("on-main", "team/base", false, "RUN make"),
                dockerfile("released", "team/base", true, "RUN make"));

        assertThat(Links.of(List.of(image), Map.of("app", new Parent.Root()), dockerfiles, Set.of()).get("app").tier())
                .isEqualTo(Link.Tier.STRONG);
    }

    @Test
    void anImageNoDockerfileAlignsWithHasNoLink() {
        Resolved.Node image = image("app", "FROM registry.example.com/team/base:1", "RUN make", "USER 1001");
        List<Dockerfile> dockerfiles = List.of(dockerfile("other", "team/base", true, "RUN npm ci", "CMD node"));

        assertThat(Links.of(List.of(image), Map.of("app", new Parent.Root()), dockerfiles, Set.of()))
                .containsEntry("app", new Link(Link.Tier.NONE, List.of(), false));
    }

    static Resolved.Node image(String digest, String fromComment, String... steps) {
        List<HistoryStep> history = new java.util.ArrayList<>();
        history.add(new HistoryStep("/bin/sh -c #(nop) ADD file:base in / ", null, false, null));
        for (int i = 0; i < steps.length; i++) {
            history.add(new HistoryStep(createdBy(steps[i]), i == 0 ? fromComment : null, false, null));
        }
        return new Resolved.Node(digest, new ImageConfig(List.of("L-" + digest), history, null, Map.of()));
    }

    static Dockerfile dockerfile(String blobSha, String fromPath, boolean released, String... steps) {
        return new Dockerfile(blobSha, java.util.Arrays.stream(steps).map(LinksTest::instruction).toList(), fromPath,
                released);
    }

    private static String createdBy(String step) {
        return step.startsWith("RUN ") ? "/bin/sh -c " + step.substring(4) : "/bin/sh -c #(nop) " + step;
    }

    private static Instruction instruction(String step) {
        int space = step.indexOf(' ');
        return new Instruction(step.substring(0, space), step.substring(space + 1));
    }
}
