package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// P3: with every capability dropped, a non-root process cannot bind a port below 1024.
final class LowPorts implements PlatformCheck {

    private static final Pattern NUMBER = Pattern.compile("(\\d{1,5})(-\\d{1,5})?(/\\w+)?");

    @Override
    public Check id() {
        return Check.P3;
    }

    @Override
    public Certainty certainty() {
        return Certainty.READ;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        return image.facts().exposedPorts().stream().flatMap(port -> number(port).stream())
                .filter(port -> port < 1024).distinct()
                .map(port -> "port " + port + " is below 1024, which OpenShift's arbitrary UID cannot bind: listen "
                        + "above 1024 and let the Service expose " + port)
                .toList();
    }

    static Optional<Integer> number(String port) {
        Matcher matcher = NUMBER.matcher(port.strip());
        return matcher.matches() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
    }
}
