package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;
import java.util.Locale;

final class AddedCapability implements PodCheck {

    @Override
    public Check id() {
        return Check.D3;
    }

    @Override
    public List<Problem> problems(Workload workload, Profile profile) {
        List<String> allowed = profile.allowedCapabilities().stream().map(AddedCapability::name).toList();
        return workload.pod().containers().stream()
                .flatMap(container -> container.security().addedCapabilities().stream()
                        .filter(capability -> !allowed.contains(name(capability)))
                        .map(capability -> new Problem("container " + container.name() + " adds " + capability,
                                Certainty.READ)))
                .toList();
    }

    static String name(String capability) {
        return capability.toUpperCase(Locale.ROOT).replaceFirst("^CAP_", "");
    }
}
