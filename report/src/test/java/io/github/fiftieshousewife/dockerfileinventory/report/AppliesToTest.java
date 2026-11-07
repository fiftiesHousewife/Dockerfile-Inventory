package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.fiftieshousewife.dockerfileinventory.report.Figures.Apart;
import io.github.fiftieshousewife.dockerfileinventory.report.Figures.Count;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppliesToTest {

    private static final Figures ESTATE = figures("estate", 1000, new Apart(0, 0), Map.of(), "x/y", 10,
            new Count(600, 1000, true), new Count(300, 1000, true));

    @Test
    void aRateOnTheOtherSideOfAThresholdFromTheEstateIsTold() {
        Figures bitnami = figures("bitnami", 100, new Apart(0, 0), Map.of(), "bitnami/containers", 20,
                new Count(9, 100, true), new Count(25, 100, true));

        assertThat(AppliesTo.sentences(bitnami, ESTATE, List.of(bitnami)))
                .containsExactly("9.0% of locations run as root (good), against 60.0% across the estate.");
    }

    @Test
    void aRateMoreThanTwentyPointsFromTheEstateIsToldEvenOnTheSameSide() {
        Figures konflux = figures("konflux-ci", 100, new Apart(0, 0), Map.of(), "konflux-ci/a", 20,
                new Count(96, 100, true), new Count(30, 100, true));

        assertThat(AppliesTo.sentences(konflux, ESTATE, List.of(konflux)))
                .containsExactly("96.0% of locations run as root (act), against 60.0% across the estate.");
    }

    @Test
    void ratesWithoutDataAreNotCompared() {
        Figures unknown = figures("acme", 100, new Apart(0, 0), Map.of(), "acme/a", 20,
                new Count(0, 100, false), new Count(30, 100, true));

        assertThat(AppliesTo.sentences(unknown, ESTATE, List.of(unknown))).isEmpty();
    }

    @Test
    void bundlesABaseFamilyNoOtherOrganisationUsesAndOneDominantRepositoryAreTold() {
        Figures konflux = figures("konflux-ci", 3231, new Apart(631, 3021), Map.of("ubi-minimal", 150, "go", 60),
                "konflux-ci/community-operators-prod", 3021, new Count(600, 1000, true), new Count(300, 1000, true));
        Figures other = figures("openshift", 10, new Apart(0, 0), Map.of("ubi-minimal", 10), "openshift/a", 10,
                new Count(600, 1000, true), new Count(300, 1000, true));

        assertThat(AppliesTo.sentences(konflux, ESTATE, List.of(konflux, other))).containsExactly(
                "3,021 locations are metadata-only bundles, left out of the user, base and archetype figures.",
                "One repository, konflux-ci/community-operators-prod, holds 93.5% of the locations.",
                "The only organisation building on go (60 locations).");
    }

    static Figures figures(String name, int locations, Apart bundles, Map<String, Integer> families,
                           String largest, int largestLocations, Count root, Count pinned) {
        return new Figures(name, locations, 2, locations, bundles, new Apart(0, 0), Map.of(), Map.of(), families,
                largest, largestLocations, Map.of(Health.ROOT, root, Health.PINNED, pinned));
    }
}
