package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CadencesTest {

    private static RegistryImage image(String digest, String parent, String created) {
        return new RegistryImage(digest, List.of("team/app:1"), parent, List.of(), 5, created,
                "2026-11-06T00:00:00Z", null, Map.of(), List.of(), null, List.of());
    }

    private static List<Instant> pushes(String... times) {
        return List.of(times).stream().map(Instant::parse).toList();
    }

    @Test
    void anImagesPushesInTheYearAreCountedBeforeTheRegistrysNewestPush() {
        Cadences cadences = new Cadences(Map.of("sha256:a", image("sha256:a", null, null)), Map.of("team/app",
                pushes("2025-01-01T00:00:00Z", "2026-03-01T00:00:00Z", "2026-09-01T00:00:00Z")),
                Instant.parse("2026-11-06T00:00:00Z"));

        assertThat(cadences.of("sha256:a").pushesInYear()).isEqualTo(2);
    }

    @Test
    void theMedianDaysBetweenPushesIsTakenOverEveryPush() {
        Cadences cadences = new Cadences(Map.of("sha256:a", image("sha256:a", null, null)), Map.of("team/app",
                pushes("2026-01-01T00:00:00Z", "2026-01-11T00:00:00Z", "2026-02-10T00:00:00Z")),
                Instant.parse("2026-11-06T00:00:00Z"));

        assertThat(cadences.of("sha256:a").medianDaysBetween()).isEqualTo(20);
    }

    @Test
    void anImagePushedOnceHasNoMedian() {
        Cadences cadences = new Cadences(Map.of("sha256:a", image("sha256:a", null, null)),
                Map.of("team/app", pushes("2026-01-01T00:00:00Z")), Instant.parse("2026-11-06T00:00:00Z"));

        assertThat(cadences.of("sha256:a").medianDaysBetween()).isNull();
    }

    @Test
    void anImageIsBuiltSomeDaysAfterItsParent() {
        Cadences cadences = new Cadences(Map.of(
                "sha256:a", image("sha256:a", "sha256:p", "2026-03-10T12:00:00Z"),
                "sha256:p", image("sha256:p", null, "2026-03-01T00:00:00Z")), Map.of(),
                Instant.parse("2026-11-06T00:00:00Z"));

        assertThat(cadences.of("sha256:a").daysAfterParent()).isEqualTo(9);
    }

    @Test
    void anImageWithoutAKnownParentHasNoDaysAfterIt() {
        Cadences cadences = new Cadences(Map.of("sha256:a", image("sha256:a", null, "2026-03-10T00:00:00Z")),
                Map.of(), Instant.parse("2026-11-06T00:00:00Z"));

        assertThat(cadences.of("sha256:a").daysAfterParent()).isNull();
    }
}
