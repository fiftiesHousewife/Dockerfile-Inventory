package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RepositoryPageTest {

    @Test
    void eachRepositoryHasAPageOfItsActions() {
        Map<String, String> pages = RepositoryPage.pages(ActionDrilldownTest.VIEW.actions(), Set.of());

        assertAll(
                () -> assertThat(pages).containsOnlyKeys("repositories/team-api.html", "repositories/team-web.html"),
                () -> assertThat(pages.get("repositories/team-web.html")).contains("P6", "b/Dockerfile",
                        "c/Dockerfile").doesNotContain("S5"));
    }

    @Test
    void aRepositorysPageIsNamedByItsSlug() {
        assertThat(RepositoryPage.path("Team/API")).isEqualTo("repositories/team-api.html");
    }

    @Test
    void aRepositorysActionsComeMostUrgentFirst() {
        String body = RepositoryPage.body("team/api", ActionDrilldownTest.VIEW.actions(), Set.of());

        assertThat(body.indexOf("P6")).isLessThan(body.indexOf("S5"));
    }

    @Test
    void anActionsRowLinksItsCheckAndItsCataloguedDockerfileByItsPathInThisRepository() {
        assertThat(RepositoryPage.row(ActionDrilldownTest.VIEW.actions().get(1), "team/api", Set.of("a")))
                .isEqualTo("<tr><td>breaks</td><td><a href=\"../checks/P6.html\">P6</a></td><td>what P6</td>"
                        + "<td><a href=\"../dockerfiles/a.html\">a/Dockerfile</a></td></tr>");
    }
}
