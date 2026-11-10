package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;
import java.util.Optional;

// A cluster pulls only from the registries its image configuration allows.
final class BaseRegistry implements PlatformCheck {

    @Override
    public Check id() {
        return Check.P7;
    }

    @Override
    public Certainty certainty() {
        return Certainty.READ;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        if (profile.allowedRegistries().isEmpty()) {
            return List.of();
        }
        return externalBase(image).filter(name -> !profile.allowedRegistries().contains(registry(name)))
                .map(name -> "the base " + name + " comes from " + registry(name) + ", which the profile does not "
                        + "allow (" + String.join(", ", profile.allowedRegistries()) + ")")
                .stream().toList();
    }

    static Optional<String> externalBase(Subject image) {
        Subject.Base base = image.facts().base();
        if (base == null || base.name() == null || base.name().equals("scratch") || base.name().contains("$")) {
            return Optional.empty();
        }
        boolean stage = image.stages().stream().anyMatch(s -> base.name().equals(s.name()));
        return stage ? Optional.empty() : Optional.of(base.name());
    }

    static String registry(String name) {
        int slash = name.indexOf('/');
        String first = slash < 0 ? "" : name.substring(0, slash);
        return ImageReference.host(first) ? first : "docker.io";
    }
}
