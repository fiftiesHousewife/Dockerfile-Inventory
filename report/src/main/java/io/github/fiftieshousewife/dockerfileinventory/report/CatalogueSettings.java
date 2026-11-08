package io.github.fiftieshousewife.dockerfileinventory.report;

record CatalogueSettings(int staleAfterMonths, int layerLimit) {

    static final CatalogueSettings DEFAULTS = new CatalogueSettings(12, 40);
}
