package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

// P8: a base by tag alone can change under the image between two builds.
final class UnpinnedBase implements PlatformCheck {

    @Override
    public Check id() {
        return Check.P8;
    }

    @Override
    public Certainty certainty() {
        return Certainty.READ;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        Subject.Base base = image.facts().base();
        return BaseRegistry.externalBase(image).filter(name -> base.digest() == null)
                .map(name -> base.tag() == null ? "the base " + name + " has no tag or digest, so it floats with latest"
                        : "the base " + name + ":" + base.tag() + " is not pinned by digest, so a rebuild may take a "
                        + "different image")
                .stream().toList();
    }
}
