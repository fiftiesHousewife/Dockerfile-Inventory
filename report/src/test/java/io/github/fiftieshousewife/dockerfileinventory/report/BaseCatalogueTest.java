package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

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

    static Estate estate(Dockerfile... dockerfiles) {
        return new Estate(List.of(dockerfiles), List.of(), List.of(), Map.of());
    }

    static Dockerfile dockerfile(String sha, Base base, int locations) {
        return new Dockerfile(sha, "t-" + sha, "s-" + sha, "sig", base, Set.of(), Set.of(), "ROOT", 0, 0,
                PassesTest.locations(sha, locations));
    }
}
