package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardTest {

    private static final Summary SUMMARY = new Summary(1234, 2,
            List.of(new PassCount("blob", 900, 1234), new PassCount("text-normal", 700, 1234),
                    new PassCount("signature", 400, 1234)),
            350, 42, 311, 0.02, 0.15, 0.25, 0.1, Map.of("microdnf", 800), Map.of("ROOT", 600, "NUMERIC", 634),
            false, 0.9);
    private static final Ranking RANKING = new Ranking(List.of(new Archetype(1, "k", 50, List.of("b"),
            "ubi-minimal:9|<script>alert(1)</script>|git", "b", List.of("org/repo"))), 1234, 42, 311);
    private static final List<BaseRow> BASES = List.of(
            new BaseRow("ubi9/ubi-minimal", "9.4", "ubi-minimal", 700, 200, "supported", null));

    @Test
    void thePageShowsTheSummarysNumbers() {
        String page = Dashboard.render(SUMMARY, RANKING, BASES);

        assertThat(page).startsWith("<!doctype html>")
                .contains("1,234").contains(">42<").contains(">311<").contains(">350<")
                .contains("ubi9/ubi-minimal").contains("microdnf").contains("25.0%");
    }

    @Test
    void textFromTheEstateIsHtmlEscaped() {
        assertThat(Dashboard.render(SUMMARY, RANKING, BASES))
                .doesNotContain("<script>alert(1)</script>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void theUserPanelSaysWhetherItShowsEffectiveUsersOrUsersAsWritten() {
        assertThat(Dashboard.render(SUMMARY, RANKING, BASES)).contains("as written in the Dockerfile");
        Summary effective = new Summary(1234, 2, SUMMARY.passes(), 350, 42, 311, 0.02, 0.15, 0.25, 0.1,
                SUMMARY.managers(), SUMMARY.users(), true, 0.9);
        assertThat(Dashboard.render(effective, RANKING, BASES)).contains("effective, from the built images");
    }
}
