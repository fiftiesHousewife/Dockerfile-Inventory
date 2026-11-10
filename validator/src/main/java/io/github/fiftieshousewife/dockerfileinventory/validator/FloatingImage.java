package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;

final class FloatingImage implements PodCheck {

    @Override
    public Check id() {
        return Check.D7;
    }

    @Override
    public List<Problem> problems(Workload workload, Profile profile) {
        return workload.pod().containers().stream()
                .filter(container -> !container.image().isBlank()
                        && ImageReference.of(container.image().strip()).floating())
                .map(container -> new Problem("container " + container.name() + " runs " + container.image().strip()
                        + ", which floats with latest", Certainty.READ))
                .toList();
    }
}
