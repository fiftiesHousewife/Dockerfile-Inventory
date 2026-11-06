package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TemplatesTest {

    @Test
    void findsTheFirstLineHoldingAJinjaExpression() {
        assertThat(Templates.jinja("FROM ubi9\nFROM {{ spec.base }}\n")).contains("FROM {{ spec.base }}");
    }

    @Test
    void anActionsExpressionAGoKeywordAndACommentAreNotJinja() {
        assertThat(Templates.jinja("ENV SHA=${{ github.sha }}\nRUN x {{ end }}\n# {{ spec.base }}\n")).isEmpty();
    }

    @Test
    void anyMarkerCountsGoTemplatesToo() {
        assertThat(Templates.anyMarker("RUN docker inspect --format '{{.Id}}' x\n"))
                .contains("RUN docker inspect --format '{{.Id}}' x");
    }
}
