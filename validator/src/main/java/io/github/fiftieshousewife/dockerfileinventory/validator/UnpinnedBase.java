package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

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
        return BaseRegistry.externalBase(image).filter(name -> base.digest() == null && !FloatingBase.floating(base))
                .map(name -> "the base " + name + ":" + base.tag() + " is not pinned by digest, so a rebuild may take "
                        + "a different image")
                .stream().toList();
    }
}
