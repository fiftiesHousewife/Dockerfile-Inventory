package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

interface PlatformCheck {

    Check id();

    Certainty certainty();

    default Certainty certainty(Subject image) {
        return certainty();
    }

    List<String> problems(Subject image, Profile profile);
}
