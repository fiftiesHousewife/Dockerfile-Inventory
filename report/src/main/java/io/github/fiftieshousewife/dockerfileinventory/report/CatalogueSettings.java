package io.github.fiftieshousewife.dockerfileinventory.report;

record CatalogueSettings(int staleAfterMonths, int layerLimit, FootprintRules footprints, LinkRule linkRule) {

    static final CatalogueSettings DEFAULTS = new CatalogueSettings(12, 40, FootprintRules.defaults(),
            LinkRule.DEFAULT);
}
