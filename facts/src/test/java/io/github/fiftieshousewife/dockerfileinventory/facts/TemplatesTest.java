package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(strings = {"FROM {{ spec.base | default('x') }}", "{% if x %}", "{{ version }}"})
    void aJinjaMarkerIsAnExpressionWithANameOrABlockTag(String line) {
        assertThat(Templates.hasJinjaMarker(line)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{{ end }}", "{{.Id}}", "${{ github.sha }}", "plain"})
    void aGoKeywordAGoFieldOrAnActionsExpressionIsNoJinjaMarker(String line) {
        assertThat(Templates.hasJinjaMarker(line)).isFalse();
    }

    @Test
    void afterDollarIsTrueOnlyWhenTheCharacterBeforeIsADollar() {
        assertThat(Templates.afterDollar("${{", 1)).isTrue();
        assertThat(Templates.afterDollar("{{", 0)).isFalse();
        assertThat(Templates.afterDollar("a{{", 1)).isFalse();
    }
}
