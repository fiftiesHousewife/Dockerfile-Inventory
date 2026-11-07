package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.BaseImage;

final class Bases {

    private Bases() {
    }

    static String reference(BaseImage base) {
        return base.tag() == null ? base.name() : base.name() + ":" + base.tag();
    }
}
