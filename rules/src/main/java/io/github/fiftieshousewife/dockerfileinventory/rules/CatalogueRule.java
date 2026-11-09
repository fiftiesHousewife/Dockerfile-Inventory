package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;

// A rule over the whole estate as the catalogue stage wrote it, for what no one Dockerfile shows.
interface CatalogueRule {

    String id();

    Severity severity();

    List<Finding> check(List<CatalogueView.Entry> estate);
}
