package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
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

    @Test
    void withNoExactMatchTheGroupsTiedForTheBestPartialAlignmentOnTheSameBaseAreAWeakLink() {
        Resolved.Node image = image("app", "FROM registry.example.com/team/base:1",
                "RUN a", "RUN b", "RUN c", "RUN d", "USER 1001");
        List<Dockerfile> dockerfiles = List.of(
                dockerfile("near", "team/base", true, "RUN a", "RUN b", "RUN c", "RUN d", "USER 1002"),
                dockerfile("near-under-repo-key", "docker-local/team/base", false, "RUN a", "RUN b", "RUN c", "RUN d"),
                dockerfile("other-base", "other/base", false, "RUN a", "RUN b", "RUN c", "RUN d", "USER 1002"),
                dockerfile("poor", "team/base", false, "RUN a", "RUN x", "RUN y", "RUN z", "USER 1001"));

        assertThat(Links.of(List.of(image), Map.of("app", new Parent.Root()), dockerfiles, Set.of()))
                .containsEntry("app", new Link(Link.Tier.WEAK, List.of("near", "near-under-repo-key"), false));
    }

    @Test
    void aRootWhoseDockerfileNamesABaseArtifactoryHoldsHasAMissingParent() {
        List<Resolved.Node> images = List.of(
                image("orphan", "FROM registry.example.com/team/base:1", "RUN make"),
                image("on-external", "FROM registry.access.redhat.com/ubi9:9.8", "RUN npm ci"),
                image("with-parent", "FROM registry.example.com/team/base:1", "RUN go build"));
        List<Dockerfile> dockerfiles = List.of(
                dockerfile("orphan-df", "docker-local/team/base", false, "RUN make"),
                dockerfile("external-df", "ubi9", false, "RUN npm ci"),
                dockerfile("parented-df", "team/base", false, "RUN go build"));
        Map<String, Parent> parents = Map.of(
                "orphan", new Parent.Root(), "on-external", new Parent.Root(), "with-parent", new Parent.Of("base"));

        Map<String, Link> links = Links.of(images, parents, dockerfiles, Set.of("team/base", "team/app"));

        assertThat(links).satisfies(l -> {
            assertThat(l.get("orphan").parentMissing()).isTrue();
            assertThat(l.get("on-external").parentMissing()).isFalse();
            assertThat(l.get("with-parent").parentMissing()).isFalse();
        });
    }

    @Test
    void aSourceLabelLimitsTheLinkToThatRepositorysDockerfilesWhoseStepsMustStillAlign() {
        Map<String, String> labels = Map.of("io.openshift.build.source-location", "https://github.com/team/app");
        Resolved.Node image = labelled("app", labels, "FROM quay.io/elsewhere/base@sha256:1",
                "RUN make", "RUN test", "RUN pack", "RUN sign", "USER 1001");
        List<Dockerfile> dockerfiles = List.of(
                dockerfile("same-steps-elsewhere", "team/base", true,
                        "RUN make", "RUN test", "RUN pack", "RUN sign", "USER 1001").in("other/copy"),
                dockerfile("in-repo", "ci/builder", false, "RUN make", "RUN test", "RUN pack", "RUN sign", "USER 0")
                        .in("team/app"),
                dockerfile("in-repo-unrelated", "ci/builder", false, "RUN npm ci").in("team/app"));

        assertThat(Links.of(List.of(image), Map.of("app", new Parent.Root()), dockerfiles, Set.of()))
                .containsEntry("app", new Link(Link.Tier.WEAK, List.of("in-repo"), false));
    }

    @Test
    void aLabelledRepositoryWithNoAligningDockerfileGivesNoLinkAndAnUnscannedOneLinksAsBefore() {
        Resolved.Node drifted = labelled("drifted",
                Map.of("org.opencontainers.image.source", "https://github.com/team/app"),
                "FROM registry.example.com/team/base:1", "RUN make");
        Resolved.Node unscanned = labelled("unscanned",
                Map.of("org.opencontainers.image.source", "https://github.com/team/unscanned"),
                "FROM registry.example.com/team/base:1", "RUN make");
        List<Dockerfile> dockerfiles = List.of(
                dockerfile("elsewhere", "team/base", false, "RUN make").in("other/copy"),
                dockerfile("in-repo", "team/base", false, "RUN npm ci").in("team/app"));

        assertThat(Links.of(List.of(drifted, unscanned),
                Map.of("drifted", new Parent.Root(), "unscanned", new Parent.Root()), dockerfiles, Set.of()))
                .containsEntry("drifted", Link.NONE)
                .containsEntry("unscanned", new Link(Link.Tier.PROBABLE, List.of("elsewhere"), false));
    }

    @Test
    void byRepositoryListsEachDockerfileUnderEveryRepositoryThatHoldsIt() {
        Dockerfile shared = dockerfile("shared", "ubi9", false, "RUN make").in("team/a").in("team/b");
        Dockerfile own = dockerfile("own", "ubi9", false, "RUN test").in("team/a");

        assertThat(Links.byRepository(List.of(shared, own)))
                .containsOnlyKeys("team/a", "team/b")
                .satisfies(byRepository -> {
                    assertThat(byRepository.get("team/a")).containsExactly(shared, own);
                    assertThat(byRepository.get("team/b")).containsExactly(shared);
                });
    }

    @Test
    void anExactGroupIsStrongWhenAnyDockerfileIsReleasedAndListsItsBlobsSorted() {
        assertThat(Links.exact(List.of(dockerfile("b", "ubi9", false), dockerfile("a", "ubi9", true))))
                .isEqualTo(new Link(Link.Tier.STRONG, List.of("a", "b"), false));
        assertThat(Links.exact(List.of(dockerfile("a", "ubi9", false))))
                .isEqualTo(new Link(Link.Tier.PROBABLE, List.of("a"), false));
    }

    @Test
    void aLabelledImagePrefersTheRepositorysDockerfilesWithItsExactSteps() {
        List<Instruction> own = List.of(instruction("RUN make"));

        assertThat(Links.labelled(own, List.of(dockerfile("same", "x", false, "RUN make"),
                dockerfile("near", "x", false, "RUN make", "RUN test"))))
                .isEqualTo(new Link(Link.Tier.PROBABLE, List.of("same"), false));
    }

    @Test
    void anUnlabelledImageWithoutAnExactGroupOrAKnownBaseHasNoLink() {
        Links links = new Links(List.of(), List.of(dockerfile("near", "team/base", false, "RUN make")), Set.of());

        assertThat(links.unlabelled(List.of(instruction("RUN make"), instruction("USER 1")), null))
                .isEqualTo(Link.NONE);
        assertThat(links.weak(List.of(instruction("RUN make")), null)).isEqualTo(Link.NONE);
    }

    @Test
    void theParentIsMissingOnlyForARootOrUnplacedImageLinkedToADockerfileOnAHeldBase() {
        Links links = new Links(List.of(), List.of(dockerfile("df", "team/base", false, "RUN make")),
                Set.of("team/base"));
        Link linked = new Link(Link.Tier.PROBABLE, List.of("df"), false);

        assertThat(links).satisfies(l -> {
            assertThat(l.parentMissing(new Parent.Root(), linked)).isTrue();
            assertThat(l.parentMissing(null, linked)).isTrue();
            assertThat(l.parentMissing(new Parent.Of("base"), linked)).isFalse();
            assertThat(l.parentMissing(new Parent.Root(), Link.NONE)).isFalse();
        });
    }

    @Test
    void aLinkedRootOnAHeldBaseIsMarkedParentMissing() {
        Resolved.Node image = image("app", "FROM registry.example.com/team/base:1", "RUN make");
        Links links = new Links(List.of(image), List.of(dockerfile("df", "team/base", false, "RUN make")),
                Set.of("team/base"));

        assertThat(links.link(image, new Parent.Root())).isEqualTo(new Link(Link.Tier.PROBABLE, List.of("df"), true));
        assertThat(links.matched(image, List.of(instruction("RUN make"))))
                .isEqualTo(new Link(Link.Tier.PROBABLE, List.of("df"), false));
    }

    static Resolved.Node image(String digest, String fromComment, String... steps) {
        List<HistoryStep> history = new ArrayList<>();
        history.add(new HistoryStep("/bin/sh -c #(nop) ADD file:base in / ", null, false, null));
        for (int i = 0; i < steps.length; i++) {
            history.add(new HistoryStep(createdBy(steps[i]), i == 0 ? fromComment : null, false, null));
        }
        return new Resolved.Node(digest, new ImageConfig(List.of("L-" + digest), history, null, Map.of()));
    }

    static Resolved.Node labelled(String digest, Map<String, String> labels, String fromComment, String... steps) {
        ImageConfig config = image(digest, fromComment, steps).config();
        return new Resolved.Node(digest, new ImageConfig(config.diffIds(), config.history(), null, labels));
    }

    static Dockerfile dockerfile(String blobSha, String fromPath, boolean released, String... steps) {
        return new Dockerfile(blobSha, Arrays.stream(steps).map(LinksTest::instruction).toList(), fromPath,
                released);
    }

    private static String createdBy(String step) {
        return step.startsWith("RUN ") ? "/bin/sh -c " + step.substring(4) : "/bin/sh -c #(nop) " + step;
    }

    static Instruction instruction(String step) {
        int space = step.indexOf(' ');
        return new Instruction(step.substring(0, space), step.substring(space + 1));
    }
}
