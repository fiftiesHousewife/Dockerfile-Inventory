package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// The arbitrary UID may have no /etc/passwd entry, so no name and no home.
final class UserIdentity implements PlatformCheck {

    private static final Pattern READS = Pattern.compile(
            "\\bwhoami\\b|\\bid -un?\\b|\\$\\{?(?:USER|HOME|LOGNAME)\\b}?|(?:^|\\s)(~)(?:/|\\s|$)");

    @Override
    public Check id() {
        return Check.P10;
    }

    @Override
    public Certainty certainty() {
        return Certainty.INFERRED;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        return Stream.of(image.config().entrypoint(), image.config().cmd()).filter(c -> c != null)
                .flatMap(c -> reads(c.arguments()).stream()).distinct()
                .map(read -> "the start command reads " + read + ", but OpenShift's arbitrary UID may have no name "
                        + "or home in /etc/passwd")
                .toList();
    }

    static Optional<String> reads(List<String> arguments) {
        Matcher matcher = READS.matcher(String.join(" ", arguments));
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1) != null ? matcher.group(1) : matcher.group());
    }
}
