package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.junit.jupiter.api.Test;

class TabsTest {

    @Test
    void theEstatesTabsAreAllThenEachOrganisationWithAllCurrent() {
        assertThat(Tabs.render(List.of("beta", "acme"), null, "")).isEqualTo("<nav class=\"tabs\" "
                + "aria-label=\"Organisations\"><a href=\"index.html\" class=\"current\" aria-current=\"page\">All</a>"
                + "<a href=\"orgs/acme.html\">acme</a><a href=\"orgs/beta.html\">beta</a></nav>");
    }

    @Test
    void anOrganisationsPageMarksItsOwnTabAndLinksUp() {
        String tabs = Tabs.render(List.of("acme", "beta"), "beta", "../");

        assertAll(
                () -> assertThat(tabs).contains("<a href=\"../index.html\">All</a>"),
                () -> assertThat(tabs).contains("<a href=\"../orgs/beta.html\" class=\"current\" "
                        + "aria-current=\"page\">beta</a>"));
    }

    @Test
    void oneOrganisationNeedsNoTabs() {
        assertThat(Tabs.render(List.of("acme"), null, "")).isEmpty();
    }
}
