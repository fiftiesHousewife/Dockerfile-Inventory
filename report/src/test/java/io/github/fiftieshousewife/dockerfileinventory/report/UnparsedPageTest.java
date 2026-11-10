package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UnparsedPageTest {

    @Test
    void eachDockerfileThatDidNotParseIsListedWithWhereItIsAndWhy() {
        assertThat(UnparsedPage.body(List.of(Entries.unparsed("zz", "beta/broken"))))
                .contains("href=\"dockerfiles/zz.html\"", "beta/broken", "mismatched input");
    }

    @Test
    void withNoneTheListSaysSo() {
        assertThat(UnparsedPage.body(List.of())).contains("Every Dockerfile parsed.");
    }
}
