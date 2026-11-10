package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

final class FloatingBase implements PlatformCheck {

    @Override
    public Check id() {
        return Check.P11;
    }

    @Override
    public Certainty certainty() {
        return Certainty.READ;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        Subject.Base base = image.facts().base();
        return BaseRegistry.externalBase(image).filter(name -> floating(base))
                .map(name -> base.tag() == null ? "the base " + name + " has no tag or digest, so it floats with latest"
                        : "the base " + name + ":" + base.tag() + " has no digest, so it floats with latest")
                .stream().toList();
    }

    static boolean floating(Subject.Base base) {
        return new ImageReference(base.name(), base.tag(), base.digest()).floating();
    }
}
