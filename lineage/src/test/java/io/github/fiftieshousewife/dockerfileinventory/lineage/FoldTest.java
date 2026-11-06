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
