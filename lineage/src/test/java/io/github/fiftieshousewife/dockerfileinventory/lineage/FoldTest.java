package io.github.fiftieshousewife.dockerfileinventory.lineage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FoldTest {

    private static final Link APP_LINK = new Link(Link.Tier.STRONG, List.of("app-df"), false);

    @Test
    void aPropertyTheImagesOwnStepsSetIsAttributedToTheImageAndItsLink() {
        Resolved.Node app = node("app", List.of("L1"), "1001", List.of(step("ADD file:base in /"), step("USER 1001")));

        EffectiveImage effective = Fold.of(List.of(app), Map.of("app", new Parent.Root()), Map.of("app", APP_LINK))
                .get("app");

        assertThat(effective.user()).isEqualTo(new Property<>("1001", new SetBy.Image("app", APP_LINK)));
    }

    @Test
    void aPropertyTheImageDoesNotSetIsAttributedToTheNearestAncestorThatDoes() {
        Link baseLink = new Link(Link.Tier.PROBABLE, List.of("base-df"), false);
        Resolved.Node ubi = node("ubi", List.of("L1"), null, List.of(step("ADD file:ubi in /")));
        Resolved.Node base = node("base", List.of("L1", "L2"), "1001",
                List.of(step("ADD file:ubi in /"), step("RUN useradd app", "FROM ubi9"), step("USER 1001")));
        Resolved.Node app = node("app", List.of("L1", "L2", "L3"), "1001", List.of(step("ADD file:ubi in /"),
                step("RUN useradd app", "FROM ubi9"), step("USER 1001"), step("RUN make", "FROM team/base:1")));
        Map<String, Parent> parents = Map.of(
                "ubi", new Parent.Root(), "base", new Parent.Of("ubi"), "app", new Parent.Of("base"));

        EffectiveImage effective = Fold.of(List.of(ubi, base, app), parents,
                Map.of("app", APP_LINK, "base", baseLink, "ubi", Links.NONE)).get("app");

        assertThat(effective.user()).isEqualTo(new Property<>("1001", new SetBy.Image("base", baseLink)));
    }

    @Test
    void workdirEntrypointAndCmdAreAttributedLikeTheUser() {
        Link baseLink = new Link(Link.Tier.PROBABLE, List.of("base-df"), false);
        Resolved.Node base = new Resolved.Node("base", new ImageConfig(List.of("L1"),
                List.of(step("ADD file:ubi in /"), step("WORKDIR /app"), step("ENTRYPOINT [\"run\"]")),
                null, Map.of(), Map.of(), "/app", List.of("run"), List.of(), List.of()));
        Resolved.Node app = new Resolved.Node("app", new ImageConfig(List.of("L1", "L2"),
                List.of(step("ADD file:ubi in /"), step("WORKDIR /app"), step("ENTRYPOINT [\"run\"]"),
                        step("RUN make"), step("CMD [\"serve\"]")),
                null, Map.of(), Map.of(), "/app", List.of("run"), List.of("serve"), List.of()));

        EffectiveImage effective = Fold.of(List.of(base, app),
                Map.of("base", new Parent.Root(), "app", new Parent.Of("base")),
                Map.of("app", APP_LINK, "base", baseLink)).get("app");

        assertThat(effective.workingDir()).isEqualTo(new Property<>("/app", new SetBy.Image("base", baseLink)));
        assertThat(effective.entrypoint()).isEqualTo(new Property<>(List.of("run"), new SetBy.Image("base", baseLink)));
        assertThat(effective.cmd()).isEqualTo(new Property<>(List.of("serve"), new SetBy.Image("app", APP_LINK)));
    }

    static HistoryStep step(String instruction) {
        return step(instruction, null);
    }

    static HistoryStep step(String instruction, String comment) {
        String createdBy = instruction.startsWith("RUN ") ? "/bin/sh -c " + instruction.substring(4)
                : "/bin/sh -c #(nop) " + instruction;
        return new HistoryStep(createdBy, comment, false, null);
    }

    static Resolved.Node node(String digest, List<String> layers, String user, List<HistoryStep> history) {
        return new Resolved.Node(digest, new ImageConfig(layers, new ArrayList<>(history), user, Map.of()));
    }
}
