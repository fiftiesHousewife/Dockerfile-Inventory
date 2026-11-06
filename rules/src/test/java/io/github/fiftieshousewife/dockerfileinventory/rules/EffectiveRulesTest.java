package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EffectiveRulesTest {

    private static final ImageView.Group APP = new ImageView.Group("app", List.of("app-df"));
    private static final ImageView.Group BASE = new ImageView.Group("base", List.of("base-df"));
    private static final ImageView.Group UBI = new ImageView.Group("ubi", List.of());

    @Test
    void s4ReportsARootNamedOrUnresolvedEffectiveUserNamingTheGroupThatSetIt() {
        EffectiveRule s4 = new ArbitraryUid();
        Map<String, String> texts = Map.of("app-df", "FROM base\nRUN chgrp -R 0 /app && chmod -R g=u /app\n");

        assertThat(s4.check(image("root", BASE), texts::get)).singleElement().satisfies(f -> {
            assertThat(f.blobSha()).isEqualTo("app-df");
            assertThat(f.contributing()).containsExactly("base-df");
            assertThat(f.message()).contains("root");
        });
        assertThat(s4.check(image("appuser", APP), texts::get)).hasSize(1);
        assertThat(s4.check(image("${UID}", APP), texts::get)).hasSize(1);
    }

    static ImageView image(String user, ImageView.Group setBy) {
        return new ImageView("app", user, setBy, List.of(APP, BASE, UBI), List.of());
    }
}
