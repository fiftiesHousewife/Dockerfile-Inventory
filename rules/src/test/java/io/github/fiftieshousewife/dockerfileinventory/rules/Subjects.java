package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;

final class Subjects {

    private Subjects() {
    }

    static Subject of(String dockerfile) {
        return DiscoveryFiles.subject(new FactsParser(), "blob", dockerfile);
    }
}
