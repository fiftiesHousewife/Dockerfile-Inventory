package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BaseCatalogueTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);

    @Test
    void basesAreGroupedByImagePathAndTagWithLocationsSummedAndThePinnedShare() {
        Estate estate = estate(
                dockerfile("a", new Base("registry.example.com/ubi9/ubi-minimal", "9.4", DIGEST), 3),
                dockerfile("b", new Base("ubi9/ubi-minimal", "9.4", null), 1),
                dockerfile("c", new Base("ubi9/ubi-minimal", "9.2", null), 2));

        assertThat(BaseCatalogue.of(estate, EolList.empty())).containsExactly(
                new BaseRow("ubi9/ubi-minimal", "9.4", "ubi-minimal", 4, 3, "unknown", null),
                new BaseRow("ubi9/ubi-minimal", "9.2", "ubi-minimal", 2, 0, "unknown", null));
    }

    @Test
    void statusComesFromTheInternalListByPathAndLongestTagPrefixEndingOnAVersionBoundary() {
        Estate estate = estate(dockerfile("a", new Base("ubi9/ubi-minimal", "9.4", null), 3),
                dockerfile("b", new Base("ubi9/ubi-minimal", "9.2-1100", null), 2),
                dockerfile("c", new Base("ubi9/ubi-minimal", "91", null), 1),
                dockerfile("d", new Base("alpine", "3.20", null), 1));

        assertThat(BaseCatalogue.of(estate, EolList.read(Path.of("src/test/resources/fixtures/eol.csv"))))
                .extracting(BaseRow::tag, BaseRow::status, BaseRow::eolDate)
                .containsExactly(
                        tuple("9.4", "supported", LocalDate.parse("2032-05-31")),
                        tuple("9.2-1100", "eol", LocalDate.parse("2025-11-30")),
                        tuple("3.20", "unknown", null),
                        tuple("91", "unknown", null));
    }

    @Test
    void anUnresolvedFromIsOneRowWhateverTheVariableIsCalled() {
        Estate estate = estate(dockerfile("a", new Base("${BASE}", null, null), 2),
                dockerfile("b", new Base("${IMAGE}", "${TAG}", null), 1));

        assertThat(BaseCatalogue.of(estate, EolList.empty()))
                .containsExactly(new BaseRow("unresolved", null, "unresolved", 3, 0, "unknown", null));
    }

    static Estate estate(Dockerfile... dockerfiles) {
        return new Estate(List.of(dockerfiles), List.of(), List.of(), Map.of());
    }

    static Dockerfile dockerfile(String sha, Base base, int locations) {
        return new Dockerfile(sha, "t-" + sha, "s-" + sha, "sig", base, Set.of(), Set.of(), "ROOT", 0, 0,
                PassesTest.locations(sha, locations), 0);
    }
}
