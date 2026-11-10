package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ArbitraryUidTest {

    private static final ImageView.Group APP = new ImageView.Group("app", List.of("app-df"));
    private static final ImageView.Group BASE = new ImageView.Group("base", List.of("base-df"));
    private static final ImageView.Group UBI = new ImageView.Group("ubi", List.of());

    private final EffectiveRule s4 = new ArbitraryUid();

    @Test
    void s4ReportsARootNamedOrUnresolvedEffectiveUserNamingTheGroupThatSetIt() {
        Map<String, String> texts = Map.of("app-df", "FROM base\nRUN chgrp -R 0 /app && chmod -R g=u /app\n");
        List<Finding> root = s4.check(image("root", BASE), texts::get);

        assertAll(
                () -> assertThat(root).hasSize(1),
                () -> assertThat(root.getFirst().blobSha()).isEqualTo("app-df"),
                () -> assertThat(root.getFirst().contributing()).containsExactly("base-df"),
                () -> assertThat(root.getFirst().message()).contains("root"),
                () -> assertThat(s4.check(image("appuser", APP), texts::get)).hasSize(1),
                () -> assertThat(s4.check(image("${UID}", APP), texts::get)).hasSize(1));
    }

    @Test
    void s4SaysRootWhenNoUserIsSetAndNamesNoGroupWhenNoImageSetIt() {
        assertThat(s4.check(image(null, null), b -> null)).singleElement()
                .extracting(Finding::message, Finding::contributing)
                .containsExactly("image app runs as root user root (none set)", List.of());
    }

    @Test
    void s4ReportsANumericUserWhenNoDockerfileInTheChainMakesPathsGroupZeroWritable() {
        Map<String, String> withChgrp = Map.of("base-df", "FROM ubi\nRUN chgrp -R 0 /opt/app && \\\n"
                + "    chmod -R g=u /opt/app\nUSER 1001\n", "app-df", "FROM base\nCOPY app.jar /opt/app/\n");
        Map<String, String> withChown = Map.of("base-df", "FROM ubi\nUSER 1001\n",
                "app-df", "FROM base\nCOPY --chown=1001:0 app.jar /opt/app/\n");
        Map<String, String> without = Map.of("base-df", "FROM ubi\nUSER 1001\n", "app-df", "FROM base\nRUN make\n");

        assertAll(
                () -> assertThat(s4.check(image("1001", BASE), withChgrp::get)).isEmpty(),
                () -> assertThat(s4.check(image("1001:0", BASE), withChown::get)).isEmpty(),
                () -> assertThat(s4.check(image("1001", BASE), without::get)).singleElement()
                        .extracting(Finding::message).asString().contains("group 0"),
                () -> assertThat(s4.check(image("1001", BASE), without::get)).singleElement()
                        .extracting(Finding::contributing).isEqualTo(List.of("app-df")));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "RUN chgrp -R 0 /app|true", "RUN chmod -R g+rwX /app|true", "RUN chmod g=u /app|true",
        "COPY --chown=1001:0 a /app|true", "RUN chown -R 1001:0 /app|true", "RUN chmod 755 /app|false",
        "RUN chown 1001:1001 /app|false"})
    void groupZeroWritableLooksForAStepThatLetsGroupZeroWrite(String step, boolean writable) {
        Map<String, String> texts = Map.of("app-df", "FROM base\n" + step + "\n");

        assertThat(ArbitraryUid.groupZeroWritable(image("1001", APP), texts::get)).isEqualTo(writable);
    }

    @Test
    void groupZeroWritableSkipsADockerfileThatWasNotFetched() {
        assertThat(ArbitraryUid.groupZeroWritable(image("1001", APP), b -> null)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(nullValues = "none", value = {
        "none, root", "'', root", "root, root", "0, root", "0:0, root", "1001, numeric", "1001:0, numeric",
        "appuser, named", "${UID}, unresolved"})
    void kindReadsTheUserPartAsTheContainerRunsIt(String user, String kind) {
        assertThat(ArbitraryUid.kind(user)).isEqualTo(kind);
    }

    static ImageView image(String user, ImageView.Group setBy) {
        return new ImageView("app", user, setBy, List.of(APP, BASE, UBI), List.of());
    }
}
