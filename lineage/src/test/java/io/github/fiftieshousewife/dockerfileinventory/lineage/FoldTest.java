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
