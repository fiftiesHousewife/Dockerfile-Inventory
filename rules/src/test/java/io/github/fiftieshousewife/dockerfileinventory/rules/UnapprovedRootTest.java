package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnapprovedRootTest {

    private static final ImageView.Group APP = new ImageView.Group("app", List.of("app-df"));
    private static final ImageView.Group BASE = new ImageView.Group("base", List.of("base-df"));
    private static final ImageView.Group UBI = new ImageView.Group("ubi", List.of());

    private final UnapprovedRoot s1 =
            new UnapprovedRoot(BaseList.read(Path.of("src/test/resources/fixtures/allow.csv")));

    @Test
    void s1ReportsAChainWhoseRootIsNotApprovedNamingTheDockerfileBuiltOnIt() {
        ImageView onUbi = new ImageView("app", "1001", APP, List.of(APP, BASE, UBI), List.of("ubi9/ubi-minimal:9.4"));
        ImageView onAlpine = new ImageView("app", "1001", APP, List.of(APP, BASE, UBI), List.of("alpine:3.20"));

        assertAll(
                () -> assertThat(s1.check(onUbi, b -> null)).isEmpty(),
                () -> assertThat(s1.check(onAlpine, b -> null)).singleElement()
                        .extracting(Finding::message).asString().contains("alpine:3.20"),
                () -> assertThat(s1.check(onAlpine, b -> null)).singleElement()
                        .extracting(Finding::contributing).isEqualTo(List.of("base-df")));
    }

    @Test
    void s1SaysNothingWhenTheRootHasNoKnownName() {
        assertThat(s1.check(new ImageView("app", "1001", APP, List.of(APP, UBI), List.of()), b -> null)).isEmpty();
    }

    @Test
    void builtOnRootIsTheDockerfilesOfTheImageJustAboveTheRoot() {
        assertAll(
                () -> assertThat(UnapprovedRoot.builtOnRoot(List.of(APP, BASE, UBI))).containsExactly("base-df"),
                () -> assertThat(UnapprovedRoot.builtOnRoot(List.of(UBI))).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"ubi9/ubi-minimal:9.4, true", "ubi9/ubi-minimal:8.10, false", "ubi9/ubi, true",
        "registry:5000/ubi9/ubi, false", "alpine:3.20, false"})
    void approvedSplitsTheTagAfterTheLastSlashOff(String name, boolean approved) {
        assertThat(s1.approved(name)).isEqualTo(approved);
    }
}
