package io.github.fiftieshousewife.dockerfileinventory.rules;

import io.github.fiftieshousewife.dockerfileinventory.facts.BaseImage;

final class Bases {

    private Bases() {
    }

    /** The base as written, with its tag; the digest is left off as it does not help a reader. */
    static String reference(BaseImage base) {
        return base.tag() == null ? base.name() : base.name() + ":" + base.tag();
    }
}
