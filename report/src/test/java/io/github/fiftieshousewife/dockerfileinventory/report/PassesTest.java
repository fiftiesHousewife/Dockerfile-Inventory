package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PassesTest {

    static final Estate ESTATE = new Estate(List.of(
            dockerfile("a", "text-1", "sig-1", 3),
            dockerfile("a-cosmetic", "text-1", "sig-1", 2),
            dockerfile("b-same-function", "text-2", "sig-1", 1),
            dockerfile("c", "text-3", "sig-2", 4)),
            List.of(failure("bad-1", 1), failure("bad-2", 2)), List.of(), Map.of());

    @Test
    void eachExactPassCountsDistinctGroupsAndLocations() {
        assertThat(Passes.exact(ESTATE)).containsExactly(
                new PassCount("blob", 6, 13),
                new PassCount("text-normal", 5, 13),
                new PassCount("signature", 3, 13));
    }

    @Test
    void signatureGroupsCarryTheirMembersPackagesAndFamilyAndFailuresFormOneGroup() {
        assertThat(Passes.signatureGroups(ESTATE)).containsExactly(
                new Group("sig-1", List.of("a", "a-cosmetic", "b-same-function"), 6, Set.of("git"), "ubi-minimal"),
                new Group("sig-2", List.of("c"), 4, Set.of("git"), "ubi-minimal"),
                new Group("parse-failures", List.of("bad-1", "bad-2"), 3, Set.of(), "parse-failures"));
        assertThat(Passes.family(new Base("${BASE}", null, null))).isEqualTo("unresolved");
    }

    @Test
    void templatesFormTheirOwnGroupApartFromParseFailures() {
        Estate estate = new Estate(List.of(), List.of(failure("bad-1", 1),
                new Failure("tpl-1", Summary.TEMPLATE + " (FROM {{ spec.base }})", locations("tpl-1", 2))),
                List.of(), Map.of());

        assertThat(Passes.signatureGroups(estate)).containsExactly(
                new Group("parse-failures", List.of("bad-1"), 1, Set.of(), "parse-failures"),
                new Group("templates", List.of("tpl-1"), 2, Set.of(), "templates"));
    }

    static Dockerfile dockerfile(String sha, String textHash, String signatureHash, int locations) {
        return new Dockerfile(sha, textHash, signatureHash, "ubi-minimal:9|microdnf|git|ROOT||NONE|stages=1",
                new Base("ubi9/ubi-minimal", "9.4", null), Set.of("git"), Set.of("microdnf"), "ROOT", 1, 0,
                locations(sha, locations));
    }

    static Failure failure(String sha, int locations) {
        return new Failure(sha, "Syntax error", locations(sha, locations));
    }

    static List<Location> locations(String sha, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new Location("org/repo-" + sha + "-" + i, "Dockerfile", "RELEASED"))
                .toList();
    }
}
