package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.util.List;

interface CatalogueRule {

    String id();

    Severity severity();

    List<Finding> check(List<CatalogueView.Entry> estate);
}
